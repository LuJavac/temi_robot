"""API d'ingestion de la telemetrie temi.

Recoit les evenements JSON du robot, valide, applique la regle de
confidentialite (aucun verbatim, aucun identifiant nominatif) et ecrit
dans InfluxDB.

Regle de confidentialite appliquee ici :
- le schema d'entree interdit tout champ inattendu (extra="forbid"),
  donc impossible d'envoyer du texte de conversation par accident ;
- les champs libres (event_type, topic, zone) sont limites a un petit
  vocabulaire de caracteres et une longueur courte : on ne peut pas y
  glisser une phrase ;
- session_id doit etre un UUID (anonyme par construction).
"""

import os
import re
import uuid
from datetime import datetime, timezone

from fastapi import Depends, FastAPI, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from influxdb_client import InfluxDBClient, Point
from influxdb_client.client.write_api import SYNCHRONOUS
from pydantic import BaseModel, Field, field_validator

API_TOKEN = os.environ["API_TOKEN"]
INFLUXDB_URL = os.environ.get("INFLUXDB_URL", "http://influxdb:8086")
INFLUXDB_TOKEN = os.environ["INFLUXDB_TOKEN"]
INFLUXDB_ORG = os.environ.get("INFLUXDB_ORG", "nyp")
INFLUXDB_BUCKET = os.environ.get("INFLUXDB_BUCKET", "temi")

# Petites etiquettes techniques uniquement : pas de place pour du verbatim.
LABEL_RE = re.compile(r"^[a-z0-9_\-]{1,32}$")

app = FastAPI(title="temi telemetry ingest", version="1.0")
bearer = HTTPBearer()

influx = InfluxDBClient(url=INFLUXDB_URL, token=INFLUXDB_TOKEN, org=INFLUXDB_ORG)
write_api = influx.write_api(write_options=SYNCHRONOUS)


def check_token(credentials: HTTPAuthorizationCredentials = Depends(bearer)) -> None:
    if credentials.credentials != API_TOKEN:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="invalid token")


class Event(BaseModel):
    """Un evenement de telemetrie. Tout champ non liste est rejete."""

    model_config = {"extra": "forbid"}

    event_type: str
    timestamp: datetime | None = None
    session_id: str | None = None
    zone: str | None = None
    topic: str | None = None
    duration_s: float | None = Field(default=None, ge=0, le=86_400)
    turn_count: int | None = Field(default=None, ge=0, le=10_000)

    @field_validator("event_type", "zone", "topic")
    @classmethod
    def validate_label(cls, v: str | None) -> str | None:
        if v is not None and not LABEL_RE.match(v):
            raise ValueError("label must match [a-z0-9_-]{1,32}")
        return v

    @field_validator("session_id")
    @classmethod
    def validate_session_id(cls, v: str | None) -> str | None:
        if v is not None:
            uuid.UUID(v)  # leve ValueError si ce n'est pas un UUID anonyme
        return v


def to_point(event: Event) -> Point:
    """Convertit un evenement vers la mesure InfluxDB correspondante."""
    ts = event.timestamp or datetime.now(timezone.utc)

    if event.event_type == "conversation":
        point = Point("conversations")
        if event.topic:
            point.tag("topic", event.topic)
        if event.session_id:
            point.tag("session_id", event.session_id)
        point.field("duration_s", float(event.duration_s or 0))
        point.field("turn_count", int(event.turn_count or 0))
    elif event.event_type == "interaction":
        point = Point("interactions")
        if event.zone:
            point.tag("zone", event.zone)
        if event.session_id:
            point.tag("session_id", event.session_id)
        point.field("duration_s", float(event.duration_s or 0))
    else:
        # Evenements ponctuels : wave, face_detected, greeting, idle, ...
        point = Point("robot_events")
        point.tag("event_type", event.event_type)
        point.field("count", 1)

    return point.time(ts)


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


@app.post("/events", status_code=status.HTTP_201_CREATED)
def ingest(event: Event, _: None = Depends(check_token)) -> dict:
    write_api.write(bucket=INFLUXDB_BUCKET, record=to_point(event))
    return {"stored": True}


@app.post("/events/batch", status_code=status.HTTP_201_CREATED)
def ingest_batch(events: list[Event], _: None = Depends(check_token)) -> dict:
    """Flush du buffer hors-ligne du robot : plusieurs evenements d'un coup."""
    if len(events) > 500:
        raise HTTPException(status_code=413, detail="batch too large (max 500)")
    write_api.write(bucket=INFLUXDB_BUCKET, record=[to_point(e) for e in events])
    return {"stored": len(events)}
