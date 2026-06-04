from llama_index.core import SimpleDirectoryReader, VectorStoreIndex, StorageContext, load_index_from_storage, Settings
from llama_index.llms.openai import OpenAI
from llama_index.embeddings.openai import OpenAIEmbedding
from llama_index.core.response_synthesizers import get_response_synthesizer
from llama_index.core import PromptTemplate


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
    "3. NEVER say 'Based on the provided context' or 'I don't have information in my context'. Just answer the user directly and naturally."
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


def build_query_engine(streaming: bool):
    """Construit un moteur de requête RAG, en mode bloquant ou streaming."""
    response_synthesizer = get_response_synthesizer(
        response_mode="compact",
        streaming=streaming,
    )
    return index.as_query_engine(
        response_synthesizer=response_synthesizer,
        similarity_top_k=4,
        text_qa_template=CUSTOM_QA_TEMPLATE,
    )


def chatbot(input_text):
    """Réponse complète (bloquante) — utilisée par /process."""
    query_engine = build_query_engine(streaming=False)

    print(f"🤖 Le robot cherche la réponse pour : '{input_text}'")
    response = query_engine.query(input_text)

    # verif des sources utilisées par le RAG
    print("\n🔍 CE QUE LE ROBOT A LU DU RAG:")
    for node in response.source_nodes:
        print(f"-> {node.node.text}")
    print("-----------------------------------------\n")

    return response.response


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
        print(f"🤖 (stream) Recherche pour : '{input_text}'")
        query_engine = build_query_engine(streaming=True)
        streaming_response = query_engine.query(input_text)
        for token in streaming_response.response_gen:
            yield f"data: {json.dumps({'delta': token})}\n\n"
        yield "data: [DONE]\n\n"

    return Response(stream_with_context(generate()), mimetype='text/event-stream')


if __name__ == '__main__':
    print("🟢 Le serveur RAG OpenAI est PRÊT !")
    # threaded=True : permet de gérer le flux SSE sans bloquer les autres requêtes
    app.run(host='0.0.0.0', port=5000, threaded=True)
