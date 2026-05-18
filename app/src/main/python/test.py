import requests

# L'adresse de ton serveur (ton propre ordinateur)
url = 'http://127.0.0.1:5000/process'

# La question que le "robot" va envoyer
data = {'text': 'Can you tell me about the sports facilities at Nyp? \n Tell me where are those facilities located ? \n What can I eat in south canteen, north, kofu, center canteen ?  when are the opening hours of each of the canteens ? What can you study at Nyp ? What are the courses offered ? What are the facilities available for students ?'}

print(f"Envoi de la question au serveur : '{data['text']}'...")

try:
    # On envoie la requête
    response = requests.post(url, json=data)
    
    # On affiche la réponse du serveur
    if response.status_code == 200:
        print("✅ Réponse de l'IA : \n")
        print(response.json()['response'])
    else:
        print(f"❌ Erreur du serveur : Code {response.status_code}")

except requests.exceptions.ConnectionError:
    print("❌ Erreur : Le serveur main.py n'est pas allumé ! (N'oublie pas de lancer 'python main.py' dans un autre terminal d'abord)")