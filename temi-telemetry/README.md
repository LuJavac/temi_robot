# temi-telemetry

Stack de télémétrie auto-hébergée sur le Raspberry Pi (cf. `temi_telemetry_pi_specs.md`
à la racine du dépôt) : API d'ingestion FastAPI + InfluxDB 2.x + Grafana, le tout en
Docker Compose.

```
Robot temi (app Android) ──POST JSON──> API (:8000) ──> InfluxDB (:8086) <── Grafana (:3001)
```

## Confidentialité

L'API ne stocke **que des métriques** (compteurs, durées, horodatages). Le schéma
d'entrée rejette tout champ inattendu, les étiquettes sont limitées à 32 caractères
`[a-z0-9_-]` (impossible d'y glisser une phrase), et les `session_id` doivent être
des UUID anonymes. Aucun verbatim de conversation ne transite ni n'est stocké.

## Démarrage

```bash
# 1. Copier la config et changer tous les secrets
cp .env.example .env
nano .env

# 2. Lancer la stack
docker compose up -d --build

# 3. Vérifier
curl http://localhost:8000/health
# -> {"status":"ok"}
```

- Grafana : http://192.168.1.43:3001 (dashboard « temi — Activité du robot » déjà provisionné)
- InfluxDB UI : http://192.168.1.43:8086
- Forgejo reste sur le port 3000 (non touché)

## Tester l'ingestion à la main

```bash
TOKEN=$(grep API_TOKEN .env | cut -d= -f2)

curl -X POST http://localhost:8000/events \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"event_type": "wave"}'
# -> {"stored":true}

curl -X POST http://localhost:8000/events/batch \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '[{"event_type":"conversation","session_id":"a1b2c3d4-e5f6-7890-abcd-ef1234567890","duration_s":42,"turn_count":3}]'
# -> {"stored":1}
```

Les points apparaissent ensuite dans Grafana (penser à régler la plage de temps).

## Côté robot (app Android)

Le client est `app/src/main/java/com/temi/temi_robot/telemetry/TelemetryClient.kt` :

- les événements sont bufferisés dans un fichier local (`telemetry_queue.jsonl`)
  et flushés par batch toutes les 30 s vers `http://192.168.1.43:8000/events/batch` —
  rien n'est perdu pendant les coupures réseau ;
- **le jeton `TELEMETRY_TOKEN` dans `TelemetryClient.kt` doit correspondre à
  `API_TOKEN` dans le `.env` du Pi.**

Événements émis : `wave`, `face_detected`, `greeting`, `idle`, et un événement
`conversation` (durée + nombre de tours, session UUID anonyme) à la fin de chaque
session d'interaction (mise en veille, retour à la base ou perte de réseau).

## Fiabilité (rappels de la spec)

- Réserver l'IP `192.168.1.43` du Pi en DHCP sur le routeur Netgear (bail statique
  lié à l'adresse MAC), sinon le robot perdra l'API après un reboot du routeur.
- Les données vivent dans les volumes Docker `influxdb-data` et `grafana-data`
  (elles survivent aux redémarrages des conteneurs ; `restart: unless-stopped`
  relance tout au boot du Pi).
- Sauvegarde périodique possible avec :
  `docker exec temi-influxdb influx backup /tmp/backup && docker cp temi-influxdb:/tmp/backup ./backup-$(date +%F)`

## Note sur les tokens InfluxDB

Par simplicité, l'API et Grafana utilisent le token admin d'InfluxDB défini dans le
`.env` (`INFLUXDB_TOKEN`). Pour suivre la spec à la lettre (token écriture pour
l'API, token lecture pour Grafana), créer deux tokens scoped dans l'UI InfluxDB
(http://192.168.1.43:8086 → Load Data → API Tokens) et les substituer dans le `.env`.
