import config
from openai import OpenAI

# On connecte la clé API
client = OpenAI(api_key=config.apikey)

print("⏳ Connexion à OpenAI en cours...")

try:
    # On pose une question simple, sans PDF
    reponse = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=[
            {"role": "user", "content": "Parle moi du robot temi en 2 lignes."}
        ]
    )
    
    print("✅ SUCCÈS ! La clé fonctionne pour discuter.")
    print("🤖 ChatGPT :", reponse.choices[0].message.content)

except Exception as e:
    print("❌ Erreur :", e)