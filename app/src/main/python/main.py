from llama_index.core import SimpleDirectoryReader, VectorStoreIndex, StorageContext, load_index_from_storage, Settings
from llama_index.llms.openai import OpenAI
from llama_index.embeddings.openai import OpenAIEmbedding
from llama_index.core.response_synthesizers import get_response_synthesizer

import config
import os
from flask import Flask, request, jsonify

app = Flask(__name__)

# 1. On connecte la clé API
os.environ["OPENAI_API_KEY"] = config.apikey

# 2. On force l'utilisation des TOUT NOUVEAUX modèles OpenAI (débloqués par défaut)
Settings.llm = OpenAI(model="gpt-4o-mini", temperature=0.7)
Settings.embed_model = OpenAIEmbedding(model="text-embedding-3-small")

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

# Initialisation de la mémoire
index = get_index()

def chatbot(input_text):
    response_synthesizer = get_response_synthesizer(
        response_mode="compact",
        streaming=False
    )
    query_engine = index.as_query_engine(
        response_synthesizer=response_synthesizer,
        similarity_top_k=2
    )
    
    print(f"🤖 Le robot cherche la réponse pour : '{input_text}'")
    response = query_engine.query(input_text)
    return response.response

@app.route('/process', methods=['POST'])
def process():
    data = request.json
    input_text = data.get('text', '')
    output_text = chatbot(input_text)
    return jsonify({'response': output_text})

if __name__ == '__main__':
    print("🟢 Le serveur RAG OpenAI est PRÊT !")
    app.run(host='0.0.0.0', port=5000)