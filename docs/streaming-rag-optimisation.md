# Streaming & RAG — améliorer la vitesse de réponse (API OpenAI)

Notes d'optimisation pour le pipeline de réponse vocale du robot Temi.

## Architecture actuelle

```
Android (OkHttp) → POST {"text": ...} → serveur Python (OpenAI + RAG)
                 → renvoie {"response": "..."} en UN SEUL BLOC
                 → l'app attend sur LoadingPage, puis parle toute la réponse d'un coup
```

Fichiers concernés :
- `LoadingPage.kt` → `sendRequestToServer()` (OkHttp `enqueue` + `body.string()`)
- `MainPage.kt` → affichage (`animateTextTypewriter`) + lecture (`RobotController.speak`)
- `RobotController.kt` → `speak()` (ligne ~271)

---

## 1. Où part le temps aujourd'hui (diagnostic)

Tout est **bloquant** :

```
[clic] → POST → serveur: (RAG retrieval) + (génération OpenAI COMPLÈTE) → réponse → l'app parle
         └──────────────── tu attends TOUT ça ────────────────┘
```

La `LoadingPage` ne fait que **cacher** l'attente. La latence perçue = récupération RAG **+**
génération entière. Pour 3 phrases, l'utilisateur attend que la 3ᵉ soit générée avant
d'entendre la 1ʳᵉ.

---

## 2. Le streaming OpenAI, c'est quoi

Au lieu d'attendre la réponse entière, OpenAI envoie les **tokens au fur et à mesure**
(Server-Sent Events). La métrique qui change : le **TTFT** (time-to-first-token),
typiquement 0,3–1 s, au lieu d'attendre les 3–8 s de génération totale.

```python
# Serveur Python — leg 1 : OpenAI → serveur
stream = client.chat.completions.create(
    model="gpt-4o-mini",
    messages=[...],          # system + contexte RAG + question
    stream=True,
)
for chunk in stream:
    delta = chunk.choices[0].delta.content
    if delta:
        yield delta          # on relaie immédiatement
```

---

## 3. Streamer de bout en bout (les 2 jambes)

**Jambe 1 — OpenAI → Python** : `stream=True` (ci-dessus).

**Jambe 2 — Python → Android** : renvoyer en `text/event-stream` au lieu d'un JSON bloc.

```python
# FastAPI
from fastapi.responses import StreamingResponse

@app.post("/ask")
def ask(q: Query):
    return StreamingResponse(generate(q.text), media_type="text/event-stream")
```

**Côté Android (OkHttp)** : lire le corps **en flux** au lieu de `body.string()`
(qui attend tout) :

```kotlin
client.newCall(req).execute().use { resp ->
    val source = resp.body!!.source()
    while (!source.exhausted()) {
        val line = source.readUtf8Line() ?: continue
        // parser le chunk SSE, accumuler, mettre à jour l'UI au fil de l'eau
    }
}
```

→ remplace le `enqueue` + `JSONObject` actuel dans `LoadingPage.sendRequestToServer`.

---

## 4. Le vrai gain pour un robot qui parle : TTS par phrase

Pour un robot, le goulot perçu ce n'est pas l'écran, c'est **la voix**. Le plus gros gain :

> Accumuler les tokens, et **dès qu'une phrase complète est prête**
> (coupe sur `.` / `!` / `?` / `\n`), l'envoyer à `RobotController.speak()`.

```kotlin
buffer += delta
val sentences = buffer.split(Regex("(?<=[.!?])\\s+"))
for (s in sentences.dropLast(1)) {
    RobotController.speak(s)   // Temi met en file → s'enchaîne tout seul
}
buffer = sentences.last()      // reste partiel
```

Le robot commence à parler après **la 1ʳᵉ phrase** au lieu de tout le paragraphe.
On peut brancher le même flux sur l'effet machine à écrire (`animateTextTypewriter`
dans `MainPage`) pour afficher le texte en même temps.

> ⚠️ Passer le **fallback Android TTS** en `QUEUE_ADD` (sinon chaque phrase coupe la
> précédente). En production, Temi `TtsRequest` met déjà les phrases en file d'attente,
> donc plusieurs `speak()` s'enchaînent correctement.

---

## 5. Le RAG et la vitesse

Le **streaming n'accélère que la génération**, pas la récupération. Le RAG ajoute de la
latence *avant* le 1ᵉʳ token :

```
embed(question) → recherche vectorielle → construction du contexte → LLM
```

Pour réduire **cette** partie :

- **Index en mémoire et chaud** : FAISS / Chroma / pgvector chargé une fois au démarrage
  du serveur, pas relu à chaque requête. Embeddings du corpus **précalculés hors-ligne**.
- **Embedding requête rapide** : `text-embedding-3-small` (rapide/peu cher) ou un modèle
  local pour éviter un aller-retour réseau.
- **top-k raisonnable** : récupérer 3–5 passages, pas 20. Moins de contexte = TTFT plus
  court **et** moins cher.
- **Prompt caching OpenAI** : mettre la partie **stable** (system prompt, instructions)
  **en tête** du prompt, et la partie **variable** (passages RAG + question) **après**.
  OpenAI met en cache le préfixe stable → TTFT réduit sur les requêtes suivantes.
- **Masquer la latence RAG** : pendant la recherche, faire dire au robot une amorce courte
  (« Let me check… ») — pour un robot c'est même plus naturel qu'un silence.

Option archi : l'**API Responses** d'OpenAI propose un `file_search` / vector stores
**managé** (RAG intégré, streaming inclus) — pourrait remplacer la brique RAG maison côté
Python. À évaluer selon qu'on veut garder la main sur la récupération ou pas.

---

## 6. Plan concret pour l'app

| Où | Changement |
|---|---|
| Serveur Python | endpoint `stream=True` + `StreamingResponse` (SSE) ; RAG : index en mémoire, top-k 3–5, prompt stable en préfixe |
| `LoadingPage.kt` | remplacer `enqueue` + `body.string()` par une lecture **en flux** ; naviguer vers MainPage **tout de suite** et streamer dedans |
| `MainPage` | recevoir le flux : `speak()` **par phrase** + typewriter live |
| `RobotController` | fallback Android TTS → `QUEUE_ADD` au lieu de `QUEUE_FLUSH` |

**Effet attendu** : la latence perçue passe de « génération + RAG complète » à
« RAG + 1ʳᵉ phrase » — souvent un facteur 2–4× sur le ressenti.

---

## Prochaines étapes possibles

- Coder le **lecteur SSE côté Android** (OkHttp + découpage en phrases + `speak`).
- Exemple d'**endpoint FastAPI streaming + RAG** complet.

(Dépend du framework Python et du vector store choisis.)
