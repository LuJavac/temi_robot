from llama_index.core import SimpleDirectoryReader, VectorStoreIndex, StorageContext, load_index_from_storage, Settings
from llama_index.llms.openai import OpenAI
from llama_index.embeddings.openai import OpenAIEmbedding
from llama_index.core.response_synthesizers import get_response_synthesizer
from llama_index.core import PromptTemplate
from openai import OpenAI as OpenAIClient   # SDK OpenAI brut, pour le vrai streaming


import config
import os
import json
from flask import Flask, request, jsonify, Response, stream_with_context

app = Flask(__name__)

# 1. On connecte la clé API
#    Préfère la variable d'environnement OPENAI_API_KEY si elle existe,
#    sinon repli sur config.apikey (à migrer : voir note de sécurité).
os.environ["OPENAI_API_KEY"] = os.environ.get("OPENAI_API_KEY", config.apikey)

# 2. On force l'utilisation des TOUT NOUVEAUX modèles OpenAI (débloqués par défaut)
Settings.llm = OpenAI(model="gpt-4o-mini", temperature=0.7)
Settings.embed_model = OpenAIEmbedding(model="text-embedding-3-small")

CHAT_MODEL = "gpt-4o-mini"
TEMPERATURE = 0.7
TOP_K = 3

# Client OpenAI brut pour la génération en streaming (lit OPENAI_API_KEY dans l'env)
oai = OpenAIClient()

# Prompt stable (en tête → éligible au prompt caching d'OpenAI).
# Le contexte RAG + la question (variables) sont injectés via {context_str} / {query_str}.
CUSTOM_PROMPT_STR = (
    "You are Temi, the AI assistant at Nanyang Polytechnic. Act as a passionate, friendly, and dynamic student ambassador. "
    "Your tone should be professional but fun, approachable, and conversational.\n"
    "TALK LIKE A REAL STUDENT: use casual, natural, everyday student language, a relaxed and energetic vibe, light enthusiasm and friendly expressions, as if a fellow student were chatting. Stay clear and respectful, but never stiff, formal or corporate.\n"
    "Here is the official database context:\n"
    "---------------------\n"
    "{context_str}\n"
    "---------------------\n"
    "User's query: {query_str}\n\n"
    "STRICT INSTRUCTIONS:\n"
    "1. FORMAT: answer in ONE single, dense paragraph. Never use bullet points, lists, line breaks or multiple paragraphs.\n"
    "2. SYNTHESIZE TO THE MAXIMUM: pack the essential idea into as few words as possible. Cut filler, repetition, intros ('Great question!') and side details. Every word must carry meaning.\n"
    "3. KEEP THE KEY CONCEPTS: even while being short, always keep the important keywords and core notions needed to actually understand the concept. Be brief, not vague.\n"
    "4. If the official context contains the answer, use it to reply accurately but briefly.\n"
    "5. If the user's query is completely unrelated to the context, COMPLETELY IGNORE the context and answer using your general AI knowledge in your fun student persona.\n"
    "6. NEVER say 'Based on the provided context' or 'I don't have information in my context'. Just answer the user directly and naturally.\n"
    "7. If the user asks about multiple things, give just one tight sentence per topic and invite them to ask for more."
)
CUSTOM_QA_TEMPLATE = PromptTemplate(CUSTOM_PROMPT_STR)

# Prompt de pré-correction : nettoie les erreurs de reconnaissance vocale AVANT le RAG.
# Indispensable car le retrieval cherche les passages avec le texte de la question :
# si le mot est mal transcrit (ex: "NYPD" au lieu de "NYP"), la recherche part sur de
# mauvais documents. On corrige donc la question avant de chercher ET de répondre.
ASR_CORRECTION_PROMPT = (
    "You fix speech-to-text transcription errors in short queries spoken to Temi, a robot "
    "assistant at Nanyang Polytechnic (NYP) library in Singapore. The speech recognition often "
    "mishears specific or local terms (for example it writes 'NYPD' when the user actually said 'NYP').\n"
    "Rewrite the query below, correcting ONLY obvious transcription mistakes. Keep the original "
    "meaning, language and wording as much as possible. Do NOT answer the query and do NOT add "
    "anything else. Return ONLY the corrected query text.\n\n"
    "Query: {query}\n"
    "Corrected query:"
)


def correct_transcription(input_text):
    """Corrige les erreurs de reconnaissance vocale via un mini-appel GPT (avant le RAG).

    En cas d'échec (réseau, API...), on retombe sur le texte brut pour ne jamais bloquer.
    """
    try:
        resp = oai.chat.completions.create(
            model=CHAT_MODEL,
            temperature=0,
            max_tokens=60,
            messages=[{"role": "user", "content": ASR_CORRECTION_PROMPT.format(query=input_text)}],
        )
        corrected = (resp.choices[0].message.content or "").strip()
        if corrected:
            if corrected != input_text:
                print(f"📝 Correction ASR : '{input_text}' -> '{corrected}'")
            return corrected
    except Exception as e:
        print(f"⚠️ Correction ASR échouée ({e}), utilisation du texte brut.")
    return input_text


def construct_index(directory_path):
    print("🧠 Lecture des fichiers PDF avec les nouveaux modèles OpenAI en cours...")
    documents = SimpleDirectoryReader(directory_path).load_data()

    # Sauvegarde sur le disque
    index = VectorStoreIndex.from_documents(documents)
    index.storage_context.persist(persist_dir="./storage")
    print("✅ Fichiers lus et mémorisés avec succès !")
    return index

def get_index():
    # Si la mémoire a déjà été créée avant, on la charge
    if os.path.exists("./storage"):
        print("🧠 Chargement de la mémoire existante...")
        storage_context = StorageContext.from_defaults(persist_dir="./storage")
        return load_index_from_storage(storage_context)
    else:
        # Sinon, on lit le dossier docs
        return construct_index("docs")

# Initialisation de la mémoire (chargée UNE fois, gardée chaude en RAM)
index = get_index()

# Retriever réutilisable : sert UNIQUEMENT à récupérer les passages pertinents.
retriever = index.as_retriever(similarity_top_k=TOP_K)


def build_prompt(input_text):
    """Récupère le contexte RAG et construit le prompt final (mêmes règles qu'avant)."""
    # Étape 0 : on corrige les erreurs de reconnaissance vocale AVANT le retrieval,
    # pour que la recherche de passages ET la réponse partent du bon texte.
    input_text = correct_transcription(input_text)

    nodes = retriever.retrieve(input_text)
    context_str = "\n\n".join(n.node.get_content() for n in nodes)

    # Debug : ce que le RAG a lu
    print("\n🔍 CE QUE LE ROBOT A LU DU RAG:")
    for n in nodes:
        print(f"-> {n.node.get_content()[:120]}...")
    print("-----------------------------------------\n")

    return CUSTOM_PROMPT_STR.format(context_str=context_str, query_str=input_text)


def chatbot(input_text):
    """Réponse complète (bloquante) — utilisée par /process. Réutilise le streaming en interne."""
    return "".join(generate_deltas(input_text))


def generate_deltas(input_text):
    """Génère la réponse token par token via l'API OpenAI en streaming."""
    print(f"🤖 Recherche pour : '{input_text}'")
    prompt = build_prompt(input_text)

    stream = oai.chat.completions.create(
        model=CHAT_MODEL,
        temperature=TEMPERATURE,
        messages=[{"role": "user", "content": prompt}],
        stream=True,
    )
    for chunk in stream:
        delta = chunk.choices[0].delta.content
        if delta:
            yield delta


@app.route('/process', methods=['POST'])
def process():
    """Endpoint historique : renvoie toute la réponse d'un coup (JSON)."""
    data = request.json
    input_text = data.get('text', '')
    output_text = chatbot(input_text)
    return jsonify({'response': output_text})


@app.route('/stream', methods=['POST'])
def stream():
    """Endpoint streaming (SSE) : envoie la réponse token par token.

    Format : chaque token est un évènement `data: {"delta": "..."}\\n\\n`,
    suivi d'un `data: [DONE]\\n\\n` final. Le token est encodé en JSON pour
    rester sûr (espaces, ponctuation, retours à la ligne dans un token).
    """
    data = request.json
    input_text = data.get('text', '')

    def generate():
        for delta in generate_deltas(input_text):
            yield f"data: {json.dumps({'delta': delta})}\n\n"
        yield "data: [DONE]\n\n"

    return Response(stream_with_context(generate()), mimetype='text/event-stream')


if __name__ == '__main__':
    print("🟢 Le serveur RAG OpenAI est PRÊT !")
    # threaded=True : permet de gérer le flux SSE sans bloquer les autres requêtes
    app.run(host='0.0.0.0', port=5000, threaded=True)
