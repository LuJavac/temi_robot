from llama_index.core import SimpleDirectoryReader, VectorStoreIndex, StorageContext, load_index_from_storage, Settings
from llama_index.llms.openai import OpenAI
from llama_index.embeddings.openai import OpenAIEmbedding
from llama_index.core.response_synthesizers import get_response_synthesizer
from llama_index.core import PromptTemplate


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
    # Le prompt stable en anglais
    custom_prompt_str = (
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
    custom_qa_template = PromptTemplate(custom_prompt_str)

    # Configuration du synthétiseur
    response_synthesizer = get_response_synthesizer(
        response_mode="compact",
        streaming=False
    )
    
    # Application au moteur de recherche
    query_engine = index.as_query_engine(
        response_synthesizer=response_synthesizer,
        similarity_top_k=4,
        text_qa_template=custom_qa_template 
    )
    
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
    data = request.json
    input_text = data.get('text', '')
    output_text = chatbot(input_text)
    return jsonify({'response': output_text})

if __name__ == '__main__':
    print("🟢 Le serveur RAG OpenAI est PRÊT !")
    app.run(host='0.0.0.0', port=5000)