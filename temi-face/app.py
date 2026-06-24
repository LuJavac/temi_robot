"""Service de reconnaissance faciale local pour le robot Temi.

100% local (données biométriques) : la tablette envoie des images, le service
détecte le visage, calcule un embedding ArcFace (InsightFace buffalo_s) et le
compare à une base SQLite. Aucune donnée ne sort du réseau local.

Endpoints :
  GET    /health              -> état du service
  GET    /persons             -> liste des personnes enrôlées (sans embeddings)
  POST   /enroll              -> name + images[] : enrôle une nouvelle personne
  POST   /recognize           -> image : renvoie {match: bool, name, score}
  DELETE /delete/{person_id}  -> supprime une personne
"""

import os
import cv2
import numpy as np
from fastapi import FastAPI, UploadFile, File, Form, HTTPException
from insightface.app import FaceAnalysis

import db

# Seuils calibrables sans rebuild (variables d'environnement).
MATCH_THRESHOLD = float(os.environ.get("MATCH_THRESHOLD", "0.50"))  # cosinus min
MATCH_MARGIN = float(os.environ.get("MATCH_MARGIN", "0.05"))        # écart vs 2e personne
MIN_DET_SCORE = float(os.environ.get("MIN_DET_SCORE", "0.50"))      # qualité min du visage

app = FastAPI(title="Temi Face Recognition", version="1.0")

# Chargé une fois au démarrage. On ne garde que détection + reconnaissance
# (pas l'âge/genre/landmarks) pour économiser la RAM sur le Pi 4.
_face = FaceAnalysis(name="buffalo_s", allowed_modules=["detection", "recognition"])
_face.prepare(ctx_id=-1, det_size=(640, 640))  # ctx_id=-1 => CPU


@app.on_event("startup")
def _startup() -> None:
    db.init_db()


def _read_image(data: bytes) -> np.ndarray:
    """Décode des octets d'image en BGR (format attendu par InsightFace)."""
    arr = np.frombuffer(data, dtype=np.uint8)
    img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    if img is None:
        raise HTTPException(status_code=400, detail="Image illisible / format non supporté")
    return img


def _largest_face(img: np.ndarray):
    """Renvoie le plus grand visage détecté (ou None), filtré sur la qualité."""
    faces = _face.get(img)
    if not faces:
        return None
    # Plus grande bbox = visage le plus proche / le plus exploitable.
    face = max(faces, key=lambda f: (f.bbox[2] - f.bbox[0]) * (f.bbox[3] - f.bbox[1]))
    if float(face.det_score) < MIN_DET_SCORE:
        return None
    return face


@app.get("/health")
def health() -> dict:
    persons = db.list_persons()
    return {
        "status": "ok",
        "persons": len(persons),
        "threshold": MATCH_THRESHOLD,
        "margin": MATCH_MARGIN,
    }


@app.get("/persons")
def persons() -> dict:
    return {"persons": db.list_persons()}


@app.post("/enroll")
async def enroll(name: str = Form(...), images: list[UploadFile] = File(...)) -> dict:
    name = name.strip()
    if not name:
        raise HTTPException(status_code=400, detail="Le prénom est obligatoire")

    embeddings: list[np.ndarray] = []
    rejected = 0
    for up in images:
        face = _largest_face(_read_image(await up.read()))
        if face is None:
            rejected += 1
            continue
        embeddings.append(face.normed_embedding.astype(np.float32))

    if not embeddings:
        raise HTTPException(
            status_code=422,
            detail="Aucun visage exploitable dans les images fournies",
        )

    person_id = db.add_person(name, embeddings)
    return {
        "person_id": person_id,
        "name": name,
        "enrolled_photos": len(embeddings),
        "rejected_photos": rejected,
    }


@app.post("/recognize")
async def recognize(image: UploadFile = File(...)) -> dict:
    face = _largest_face(_read_image(await image.read()))
    if face is None:
        return {"match": False, "reason": "no_face"}

    pid, score, second = db.best_match(face.normed_embedding.astype(np.float32))
    if pid is None:
        return {"match": False, "reason": "empty_db"}

    confident = score >= MATCH_THRESHOLD and (score - second) >= MATCH_MARGIN
    if not confident:
        return {"match": False, "reason": "uncertain", "score": round(score, 3)}

    return {
        "match": True,
        "person_id": pid,
        "name": db.get_name(pid),
        "score": round(score, 3),
        "runner_up": round(second, 3),
    }


@app.delete("/delete/{person_id}")
def delete(person_id: int) -> dict:
    if not db.delete_person(person_id):
        raise HTTPException(status_code=404, detail="Personne introuvable")
    return {"deleted": person_id}
