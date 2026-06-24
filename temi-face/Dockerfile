FROM python:3.11-bookworm

WORKDIR /app

# Dépendances système : build tools pour insightface, libGL/glib pour OpenCV.
RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential cmake libglib2.0-0 libgomp1 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Pré-télécharge le modèle buffalo_s DANS l'image (pas de download au runtime).
RUN python -c "from insightface.app import FaceAnalysis; \
    FaceAnalysis(name='buffalo_s', allowed_modules=['detection','recognition']).prepare(ctx_id=-1)"

COPY . .

EXPOSE 8001
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8001"]
