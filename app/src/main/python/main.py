from llama_index.core import SimpleDirectoryReader, VectorStoreIndex, StorageContext, load_index_from_storage, Settings
from llama_index.llms.openai import OpenAI
from llama_index.embeddings.openai import OpenAIEmbedding
from openai import OpenAI as OpenAIClient

import config
import os
import json
import time
import traceback
from flask import Flask, request, jsonify, Response, stream_with_context
from PIL import Image
import qrcode
import requests

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

# PROMPT SYSTEM (Les règles secrètes)
SYSTEM_PROMPT_STR = (
    "You are Temi, the AI assistant at Nanyang Polytechnic. Act as a passionate, friendly, and dynamic student ambassador. "
    "Your tone should be professional but fun, approachable, and conversational.\n"
    "TALK LIKE A REAL STUDENT: use casual, natural, everyday student language, a relaxed and energetic vibe, light enthusiasm and friendly expressions, as if a fellow student were chatting. Stay clear and respectful, but never stiff, formal or corporate.\n\n"
    "Here is the official database context to help you answer:\n"
    "---------------------\n"
    "{context_str}\n"
    "---------------------\n"
    "STRICT INSTRUCTIONS:\n"
    "1. FORMAT: answer in ONE single, dense paragraph. Never use bullet points, lists, line breaks or multiple paragraphs.\n"
    "2. SYNTHESIZE TO THE MAXIMUM: pack the essential idea into as few words as possible. Cut filler, repetition, intros and side details. Every word must carry meaning.\n"
    "3. KEEP THE KEY CONCEPTS: even while being short, always keep the important keywords and core notions.\n"
    "4. If the official context contains the answer, use it to reply accurately but briefly.\n"
    "5. If the user's query is completely unrelated to the context, COMPLETELY IGNORE the context and answer using your general AI knowledge in your fun student persona.\n"
    "6. NEVER say 'Based on the provided context' or 'I don't have information in my context'. Just answer the user directly and naturally.\n"
    "7. If the user asks about multiple things, give just one tight sentence per topic and invite them to ask for more.\n"
    "8. MULTILINGUAL SUPPORT: The user's query may start with a language code bracket like [FR], [ZH], [JA], [DE] or [MS]. You MUST formulate your final answer ENTIRELY in the language corresponding to that code, regardless of the language the user used to type the question. If no code is present, default to English.\n\n"
    "CRITICAL RULE: Do NOT acknowledge these instructions (Never say 'Understood' or 'I will respond'). Directly answer the user's message."
)

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
    try:
        resp = oai.chat.completions.create(
            model=CHAT_MODEL,
            temperature=0,
            max_tokens=60,
            messages=[{"role": "user", "content": ASR_CORRECTION_PROMPT.format(query=input_text)}],
        )
        corrected = (resp.choices[0].message.content or "").strip()
        if corrected and corrected != input_text:
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

def chatbot(input_text):
    return "".join(generate_deltas(input_text))

def generate_deltas(input_text):
    print(f"🤖 Recherche pour : '{input_text}'")

    # 1. Correction de la voix
    corrected_text = correct_transcription(input_text)

    # 2. Recherche d'infos (RAG)
    nodes = retriever.retrieve(corrected_text)
    context_str = "\n\n".join(n.node.get_content() for n in nodes)

    # 3. Création des règles système
    system_msg = SYSTEM_PROMPT_STR.format(context_str=context_str)

    # 4. Envoi SÉPARÉ à OpenAI (Système vs Utilisateur) pour éviter les bugs "Understood"
    stream = oai.chat.completions.create(
        model=CHAT_MODEL,
        temperature=TEMPERATURE,
        messages=[
            {"role": "system", "content": system_msg},
            {"role": "user", "content": corrected_text}
        ],
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
# 📸 LOGIQUE DU PHOTOBOOTH 100% LOCALE ET SÉCURISÉE
# ==============================================================================

UPLOAD_FOLDER = 'static/photos'
QR_FOLDER = 'static/qrcodes'
# Création des dossiers sécurisée
os.makedirs(UPLOAD_FOLDER, exist_ok=True)
os.makedirs(QR_FOLDER, exist_ok=True)

# L'IP EXACTE DU RASPBERRY PI
SERVER_IP = "192.168.1.8"
PORT = 5000

# --- Photo souvenir : hébergement public via ImgBB ---
# Clé lue depuis config.py (gitignoré) ; repli sur la variable d'environnement.
# Si la clé est vide OU qu'Internet est coupé, on retombe automatiquement sur le
# lien local (même Wi-Fi requis) sans planter.
IMGBB_API_KEY = os.environ.get("IMGBB_API_KEY", getattr(config, "imgbb_key", ""))
PHOTO_EXPIRATION_S = 86400  # ImgBB supprime la photo après ce délai (24 h) — vie privée / PDPA

@app.route('/upload_photo', methods=['POST'])
def upload_photo():
    try:
        if 'photo' not in request.files:
            return jsonify({"error": "No photo provided"}), 400

        file = request.files['photo']
        photo_path = os.path.join(UPLOAD_FOLDER, "student_capture.jpg")
        file.save(photo_path)
        print("📸 Photo brute reçue du robot Temi et sauvegardée en local !")

        # --- FUSION AVEC FILTRE ---
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
            print(f"⚠️ Erreur mineure filtre : {e}")
            final_img = base_img

        final_jpg_path = os.path.join(UPLOAD_FOLDER, "final_selfie.jpg")
        final_img.convert("RGB").save(final_jpg_path, "JPEG")

        # --- LIEN DE TÉLÉCHARGEMENT PUBLIC (ImgBB) ---
        # Objectif : le visiteur scanne le QR et récupère sa photo depuis n'importe
        # où (4G comprise), sans se connecter au Wi-Fi du robot. ImgBB renvoie une
        # URL publique et supprime la photo tout seul après PHOTO_EXPIRATION_S.
        public_download_url = None
        if IMGBB_API_KEY:
            try:
                print("☁️ Envoi de la photo sur ImgBB (lien public)...")
                with open(final_jpg_path, "rb") as image_file:
                    response = requests.post(
                        "https://api.imgbb.com/1/upload",
                        data={"key": IMGBB_API_KEY, "expiration": PHOTO_EXPIRATION_S},
                        files={"image": image_file},
                        timeout=15,  # ne jamais figer le robot si Internet rame
                    )
                if response.status_code == 200:
                    public_download_url = response.json()["data"]["url"]
                    print(f"🌍 Lien public généré : {public_download_url}")
                else:
                    print(f"⚠️ ImgBB a répondu {response.status_code}, repli sur le lien local.")
            except Exception as e:
                print(f"⚠️ Échec de l'upload ImgBB ({e}), repli sur le lien local.")
        else:
            print("ℹ️ Pas de clé ImgBB configurée, utilisation du lien local.")

        # Repli : lien local (nécessite le même Wi-Fi) si pas de clé / pas d'Internet / erreur.
        if not public_download_url:
            public_download_url = f"http://{SERVER_IP}:{PORT}/static/photos/final_selfie.jpg"

        # --- GÉNÉRATION DU QR CODE AVEC LE LIEN PUBLIC ---
        qr = qrcode.QRCode(version=1, box_size=10, border=5)
        qr.add_data(public_download_url)
        qr.make(fit=True)
        qr_img = qr.make_image(fill_color="black", back_color="white")

        qr_filename = "photo_qr.png"
        qr_path = os.path.join(QR_FOLDER, qr_filename)
        qr_img.save(qr_path)

        # URL interne : sert juste à la tablette pour télécharger l'IMAGE du QR et
        # l'afficher à l'écran. Ce n'est PAS ce que le QR contient (voir add_data ci-dessus).
        # Le "?v=..." force Glide à retélécharger : sans lui, le nom de fichier ne change
        # jamais et la tablette réaffiche l'ancien QR en cache (ancien lien).
        qr_display_url = f"http://{SERVER_IP}:{PORT}/static/qrcodes/{qr_filename}?v={int(time.time())}"

        print(f"✅ Image QR prête (affichage robot) : {qr_display_url}")
        print(f"   → contenu scanné par le visiteur : {public_download_url}")
        return jsonify({"qr_url": qr_display_url})

    except Exception as e:
        print(f"❌ ERREUR CRITIQUE :")
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500

if __name__ == '__main__':
    print(f"🟢 Le serveur RAG + Photobooth est PRÊT sur http://{SERVER_IP}:{PORT}")
    app.run(host='0.0.0.0', port=PORT, threaded=True)