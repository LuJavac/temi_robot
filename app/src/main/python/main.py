from llama_index.core import SimpleDirectoryReader, VectorStoreIndex, StorageContext, load_index_from_storage, Settings
from llama_index.llms.openai import OpenAI
from llama_index.embeddings.openai import OpenAIEmbedding
from llama_index.core.response_synthesizers import get_response_synthesizer
from llama_index.core import PromptTemplate
from openai import OpenAI as OpenAIClient

import config
import os
import json
import traceback
from flask import Flask, request, jsonify, Response, stream_with_context
from PIL import Image
import qrcode

# Flask gère automatiquement le dossier 'static', pas besoin de route supplémentaire
app = Flask(__name__, static_folder='static', static_url_path='/static')

# 1. Clé API
os.environ["OPENAI_API_KEY"] = os.environ.get("OPENAI_API_KEY", config.apikey)

# 2. Modèles OpenAI
Settings.llm = OpenAI(model="gpt-4o-mini", temperature=0.7)
Settings.embed_model = OpenAIEmbedding(model="text-embedding-3-small")

CHAT_MODEL = "gpt-4o-mini"
TEMPERATURE = 0.7
TOP_K = 3

oai = OpenAIClient()

CUSTOM_PROMPT_STR = (
    "You are Temi... (Texte gardé intact pour la concision de l'affichage)"
    "8. MULTILINGUAL SUPPORT: The user's query may start with a language code bracket like [FR], [ZH], [JA], [DE] or [MS]. You MUST formulate your final answer ENTIRELY in the language corresponding to that code, regardless of the language the user used to type the question. If no code is present, default to English."
)

ASR_CORRECTION_PROMPT = (
    "You fix speech-to-text transcription errors... (Texte gardé intact)"
)

def correct_transcription(input_text):
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
    index = VectorStoreIndex.from_documents(documents)
    index.storage_context.persist(persist_dir="./storage")
    print("✅ Fichiers lus et mémorisés avec succès !")
    return index

def get_index():
    if os.path.exists("./storage"):
        print("🧠 Chargement de la mémoire existante...")
        storage_context = StorageContext.from_defaults(persist_dir="./storage")
        return load_index_from_storage(storage_context)
    else:
        return construct_index("docs")

index = get_index()
retriever = index.as_retriever(similarity_top_k=TOP_K)

def build_prompt(input_text):
    input_text = correct_transcription(input_text)
    nodes = retriever.retrieve(input_text)
    context_str = "\n\n".join(n.node.get_content() for n in nodes)
    return CUSTOM_PROMPT_STR.format(context_str=context_str, query_str=input_text)

def chatbot(input_text):
    return "".join(generate_deltas(input_text))

def generate_deltas(input_text):
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
    data = request.json
    input_text = data.get('text', '')
    output_text = chatbot(input_text)
    return jsonify({'response': output_text})

@app.route('/stream', methods=['POST'])
def stream():
    data = request.json
    input_text = data.get('text', '')
    def generate():
        for delta in generate_deltas(input_text):
            yield f"data: {json.dumps({'delta': delta})}\n\n"
        yield "data: [DONE]\n\n"
    return Response(stream_with_context(generate()), mimetype='text/event-stream')


# ==============================================================================
# 📸 LOGIQUE DU PHOTOBOOTH 100% SÉCURISÉE
# ==============================================================================

UPLOAD_FOLDER = 'static/photos'
QR_FOLDER = 'static/qrcodes'
# Création des dossiers sécurisée
os.makedirs(UPLOAD_FOLDER, exist_ok=True)
os.makedirs(QR_FOLDER, exist_ok=True)

# L'IP EXACTE DU RASPBERRY PI
SERVER_IP = "192.168.1.8"
PORT = 5000

@app.route('/upload_photo', methods=['POST'])
def upload_photo():
    try:
        if 'photo' not in request.files:
            print("❌ ERREUR : Aucune donnée 'photo' reçue de la part du Temi.")
            return jsonify({"error": "No photo provided"}), 400

        file = request.files['photo']
        photo_path = os.path.join(UPLOAD_FOLDER, "student_capture.jpg")
        file.save(photo_path)
        print("📸 Photo brute reçue du robot Temi et sauvegardée sur le Raspberry !")

        base_img = Image.open(photo_path).convert("RGBA")

        try:
            if os.path.exists("filter.png"):
                filter_img = Image.open("filter.png").convert("RGBA")
                filter_img = filter_img.resize(base_img.size)
                final_img = Image.alpha_composite(base_img, filter_img)
                print("🎨 Filtre 'filter.png' appliqué avec succès !")
            else:
                final_img = base_img
        except Exception as e:
            print(f"⚠️ Erreur mineure lors de l'application du filtre : {e}")
            final_img = base_img

        final_jpg_path = os.path.join(UPLOAD_FOLDER, "final_selfie.jpg")
        final_img.convert("RGB").save(final_jpg_path, "JPEG")

        download_url = f"http://{SERVER_IP}:{PORT}/static/photos/final_selfie.jpg"

        qr = qrcode.QRCode(version=1, box_size=10, border=5)
        qr.add_data(download_url)
        qr.make(fit=True)
        qr_img = qr.make_image(fill_color="black", back_color="white")

        qr_filename = "photo_qr.png"
        qr_path = os.path.join(QR_FOLDER, qr_filename)
        qr_img.save(qr_path)

        qr_display_url = f"http://{SERVER_IP}:{PORT}/static/qrcodes/{qr_filename}"

        print(f"✅ QR Code généré et prêt : {qr_display_url}")
        return jsonify({"qr_url": qr_display_url})

    except Exception as e:
        # S'il y a un plantage serveur (permissions Linux, bug d'image), on l'attrape et on l'affiche !
        print(f"❌ ERREUR CRITIQUE PENDANT LE TRAITEMENT DE LA PHOTO :")
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500

if __name__ == '__main__':
    print(f"🟢 Le serveur RAG + Photobooth est PRÊT sur http://{SERVER_IP}:{PORT}")
    app.run(host='0.0.0.0', port=PORT, threaded=True)