# temi-face

Service de reconnaissance faciale **100% local** pour le robot Temi (école NYP).
Quand le robot voit une personne, il envoie une image ici ; le service répond
avec le prénom si la personne est connue. L'enrôlement (1re rencontre) stocke
plusieurs photos sous un prénom.

Données biométriques : tout reste sur le Pi (SQLite local), rien ne part en
cloud. Autorisation RGPD donnée par le tuteur. Endpoint `/delete` pour retirer
une personne.

## Pile

- **FastAPI** (port 8001) en **Docker** (`restart: unless-stopped`).
- **InsightFace ArcFace** (pack `buffalo_s`, modules détection + reconnaissance
  seulement) via **onnxruntime** CPU.
- **SQLite** (volume Docker `face-data`) : embeddings 512-d, comparaison cosinus
  en mémoire.

## Déploiement sur le Pi

```bash
# Avant tout : vérifier l'espace disque (l'image fait ~1.5-2 Go)
df -h /

# Build + lancement (le build télécharge le modèle, compte ~15-30 min sur Pi 4)
cd ~/temi-robot/temi-face
docker compose up -d --build

# Vérifier
curl http://localhost:8001/health
# -> {"status":"ok","persons":0,...}
```

## Tester à la main (sur PC ou Pi)

```bash
# Enrôler une personne avec plusieurs photos (multi-angles recommandé)
curl -X POST http://localhost:8001/enroll \
  -F "name=Alex" \
  -F "images=@face1.jpg" -F "images=@face2.jpg" -F "images=@face3.jpg"

# Reconnaître
curl -X POST http://localhost:8001/recognize -F "image=@test.jpg"
# -> {"match":true,"name":"Alex","score":0.71,...}
#    ou {"match":false,"reason":"uncertain"|"no_face"|"empty_db"}

# Lister / supprimer
curl http://localhost:8001/persons
curl -X DELETE http://localhost:8001/delete/1
```

## Réglage des seuils (sans rebuild)

Dans `docker-compose.yml`, puis `docker compose up -d` :

- `MATCH_THRESHOLD` (def. 0.50) : cosinus minimum pour accepter un match.
  Monter si confusions entre personnes ; descendre si ne reconnaît pas assez.
- `MATCH_MARGIN` (def. 0.05) : écart minimum entre la 1re et la 2e personne.
  Évite de trancher entre deux personnes proches → renvoie `uncertain`.
- `MIN_DET_SCORE` (def. 0.50) : qualité minimale du visage (rejette flou/de biais).

## Côté tablette (à faire)

CameraX + MediaPipe (déjà dans le projet) pour le cadrage live en miroir,
capture multi-angles guidée, POST vers `/enroll` et `/recognize`, puis TTS
« Bonjour {prénom} ». Voir la note mémoire `face-recognition-feature`.

> ⚠️ Contention caméra : la capture faciale doit mettre en pause le wave
> detector ET la détection native Temi (elles tiennent la caméra frontale).
