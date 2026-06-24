"""Stockage SQLite des personnes + empreintes faciales (embeddings ArcFace 512-d).

Conçu pour rester 100% local (données biométriques). Les embeddings sont gardés
en mémoire sous forme de matrice numpy pour une comparaison cosinus instantanée,
et rechargés à chaque modification.
"""

import os
import sqlite3
import threading
import numpy as np

DB_PATH = os.environ.get("FACE_DB_PATH", "/data/faces.db")

# Dimension d'un embedding ArcFace (buffalo_s).
EMB_DIM = 512

_lock = threading.Lock()

# Cache mémoire : matrice (N, 512) des embeddings + person_id aligné par ligne.
_matrix = np.zeros((0, EMB_DIM), dtype=np.float32)
_person_ids: list[int] = []


def _connect() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


def init_db() -> None:
    os.makedirs(os.path.dirname(DB_PATH) or ".", exist_ok=True)
    with _connect() as conn:
        conn.execute(
            """CREATE TABLE IF NOT EXISTS persons (
                   id         INTEGER PRIMARY KEY AUTOINCREMENT,
                   name       TEXT NOT NULL,
                   created_at TEXT NOT NULL DEFAULT (datetime('now'))
               )"""
        )
        conn.execute(
            """CREATE TABLE IF NOT EXISTS embeddings (
                   id        INTEGER PRIMARY KEY AUTOINCREMENT,
                   person_id INTEGER NOT NULL REFERENCES persons(id) ON DELETE CASCADE,
                   vec       BLOB NOT NULL
               )"""
        )
    reload_cache()


def reload_cache() -> None:
    """Recharge la matrice d'embeddings en mémoire depuis SQLite."""
    global _matrix, _person_ids
    with _connect() as conn:
        rows = conn.execute("SELECT person_id, vec FROM embeddings").fetchall()
    if rows:
        _person_ids = [r[0] for r in rows]
        _matrix = np.stack([np.frombuffer(r[1], dtype=np.float32) for r in rows])
    else:
        _person_ids = []
        _matrix = np.zeros((0, EMB_DIM), dtype=np.float32)


def add_person(name: str, embeddings: list[np.ndarray]) -> int:
    """Crée une personne et stocke ses embeddings (déjà L2-normalisés). Renvoie l'id."""
    with _lock, _connect() as conn:
        cur = conn.execute("INSERT INTO persons(name) VALUES (?)", (name,))
        pid = cur.lastrowid
        for emb in embeddings:
            conn.execute(
                "INSERT INTO embeddings(person_id, vec) VALUES (?, ?)",
                (pid, emb.astype(np.float32).tobytes()),
            )
    reload_cache()
    return pid


def add_embeddings(person_id: int, embeddings: list[np.ndarray]) -> None:
    """Ajoute des embeddings à une personne existante (ré-enrôlement)."""
    with _lock, _connect() as conn:
        for emb in embeddings:
            conn.execute(
                "INSERT INTO embeddings(person_id, vec) VALUES (?, ?)",
                (person_id, emb.astype(np.float32).tobytes()),
            )
    reload_cache()


def delete_person(person_id: int) -> bool:
    with _lock, _connect() as conn:
        cur = conn.execute("DELETE FROM persons WHERE id = ?", (person_id,))
        deleted = cur.rowcount > 0
    reload_cache()
    return deleted


def get_name(person_id: int) -> str | None:
    with _connect() as conn:
        row = conn.execute("SELECT name FROM persons WHERE id = ?", (person_id,)).fetchone()
    return row[0] if row else None


def list_persons() -> list[dict]:
    with _connect() as conn:
        rows = conn.execute(
            """SELECT p.id, p.name, p.created_at, COUNT(e.id)
               FROM persons p LEFT JOIN embeddings e ON e.person_id = p.id
               GROUP BY p.id ORDER BY p.name"""
        ).fetchall()
    return [{"id": r[0], "name": r[1], "created_at": r[2], "num_photos": r[3]} for r in rows]


def best_match(query: np.ndarray) -> tuple[int | None, float, float]:
    """Compare un embedding à toute la base.

    Renvoie (person_id du meilleur match, meilleur score cosinus, 2e meilleur
    score parmi les AUTRES personnes). Les embeddings étant L2-normalisés, le
    cosinus = produit scalaire. On agrège par personne en gardant le max.
    """
    if _matrix.shape[0] == 0:
        return None, 0.0, 0.0

    sims = _matrix @ query.astype(np.float32)  # (N,)

    # Meilleur score par personne.
    per_person: dict[int, float] = {}
    for pid, s in zip(_person_ids, sims):
        if s > per_person.get(pid, -1.0):
            per_person[pid] = float(s)

    ranked = sorted(per_person.items(), key=lambda kv: kv[1], reverse=True)
    best_pid, best_score = ranked[0]
    second_score = ranked[1][1] if len(ranked) > 1 else 0.0
    return best_pid, best_score, second_score
