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
TOP_K = 6

# Client OpenAI brut pour la génération en streaming (lit OPENAI_API_KEY dans l'env)
oai = OpenAIClient()

# Prompt stable (en tête → éligible au prompt caching d'OpenAI).
# Le contexte RAG + la question (variables) sont injectés via {context_str} / {query_str}.
CUSTOM_PROMPT_STR = (
    "You are Temi, a smart, friendly, and helpful robot assistant at NYP.\n"
    "Here is the official database context:\n"
    "---------------------\n"
    "{context_str}\n"
    "---------------------\n"
    "User's query: {query_str}\n\n"
    "STRICT INSTRUCTIONS:\n"
    "1. If the official context contains the answer, use it to reply accurately.\n"
    "2. If the user's query is completely unrelated to the context (e.g., general knowledge, animals like unicorns/kangaroos, jokes, small talk), COMPLETELY IGNORE the context and answer using your general AI knowledge in a friendly way.\n"
    "3. NEVER say 'Based on the provided context' or 'I don't have information in my context'. Just answer the user directly and naturally.\n"
    "4. If the user's query contains several distinct topics or questions, address EACH one thoroughly, giving each its own dedicated paragraph. Be comprehensive and do not merge unrelated topics into a single short answer."
)
CUSTOM_QA_TEMPLATE = PromptTemplate(CUSTOM_PROMPT_STR)


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
