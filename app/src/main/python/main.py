from llama_index.core import SimpleDirectoryReader, VectorStoreIndex,PromptHelper,StorageContext,load_index_from_storage
from llama_index.llms.openai import OpenAI
from llama_index.core.llms import LLM
from llama_index.core.response_synthesizers import get_response_synthesizer

import config
import sys
import os
from flask import Flask, request, jsonify
app = Flask(__name__)
os.environ["OPENAI_API_KEY"] = config.apikey


def construct_index(directory_path):
    max_input_size = 4096
    num_outputs = 512
    max_chunk_overlap = 0.2
    chunk_size_limit = 600

    llm = OpenAI(
        temperature=0.7,
        model="text-davinci-003",  # Note: "model" instead of "model_name"
        max_tokens=num_outputs
    )

    documents = SimpleDirectoryReader(directory_path).load_data()

    # Save to disk
    index = VectorStoreIndex.from_documents(documents,llm=llm)
    index.storage_context.persist(persist_dir="./storage")
    return index

def chatbot(input_text):
    # Create query engine with desired configuration

    storage_context = StorageContext.from_defaults(persist_dir="./storage")
    index = load_index_from_storage(storage_context)
    response_synthesizer = get_response_synthesizer(
        response_mode="compact",  # Now works here
        streaming=False
    )
    query_engine = index.as_query_engine(
    response_synthesizer=response_synthesizer,
    similarity_top_k=2
    )
    response = query_engine.query(input_text)
    return response.response

# Initialize index when starting the app
index = construct_index("docs")

@app.route('/process', methods=['POST'])
def process():
    data = request.json
    input_text = data.get('text', '')
    output_text = chatbot(input_text)  # Traitement : ici juste mettre en majuscule
    return jsonify({'response': output_text})

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)
