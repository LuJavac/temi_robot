from llama_index.core import SimpleDirectoryReader, VectorStoreIndex, PromptHelper, StorageContext, load_index_from_storage
from llama_index.llms.openai import OpenAI
from llama_index.core.response_synthesizers import get_response_synthesizer

import config
import sys
import os
from flask import Flask, request, jsonify

app = Flask(__name__)

# On utilise la clé OpenAI du fichier config
os.environ["OPENAI_API_KEY"] = config.apikey

def construct_index(directory_path):
    print("Lecture des fichiers PDF avec OpenAI en cours...")
    num_outputs = 490

    # Utilisation d'un modèle OpenAI à jour
    llm = OpenAI(
        temperature=0.7,
        model="gpt-3.5-turbo-instruct", 
        max_tokens=num_outputs
    )

    documents = SimpleDirectoryReader(directory_path).load_data()

    # Sauvegarde sur le disque
    index = VectorStoreIndex.from_documents(documents, llm=llm)
    index.storage_context.persist(persist_dir="./storage")
    print("✅ Fichiers mémorisés")
    return index

def chatbot(input_text):
    storage_context = StorageContext.from_defaults(persist_dir="./storage")
    index = load_index_from_storage(storage_context)
    response_synthesizer = get_response_synthesizer(
        response_mode="compact",
        streaming=False
    )
    query_engine = index.as_query_engine(
        response_synthesizer=response_synthesizer,
        similarity_top_k=2
    )
    response = query_engine.query(input_text)
    return response.response

# On force la lecture si le dossier storage n'existe pas, sinon on charge
if not os.path.exists("./storage"):
    index = construct_index("docs")
else:
    print("Chargement de la mémoire existante...")
    storage_context = StorageContext.from_defaults(persist_dir="./storage")
    index = load_index_from_storage(storage_context)

@app.route('/process', methods=['POST'])
def process():
    data = request.json
    input_text = data.get('text', '')
    output_text = chatbot(input_text)
    return jsonify({'response': output_text})

if __name__ == '__main__':
    print("🟢 Serveur IA OpenAI prêt")
    app.run(host='0.0.0.0', port=5000)