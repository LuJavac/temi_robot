import requests

# L'adresse de ton serveur (ton propre ordinateur)
url = 'http://127.0.0.1:5000/process'

# La question que le "robot" va envoyer
data = {'text': 'Quelles sont les bourses disponibles ?'}

print(f"Envoi de la question au serveur : '{data['text']}'...")

try:
    # On envoie la requête
    response = requests.post(url, json=data)
    
    # On affiche la réponse du serveur
    if response.status_code == 200:
        print("✅ Réponse de l'IA :")
        print(response.json()['response'])
    else:
        print(f"❌ Erreur du serveur : Code {response.status_code}")

except requests.exceptions.ConnectionError:
    print("❌ Erreur : Le serveur main.py n'est pas allumé ! (N'oublie pas de lancer 'python main.py' dans un autre terminal d'abord)")