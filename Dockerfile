# Minimal Python image for the scanner API. ClamAV (clamd) is NOT in this image:
# it runs as a separate sidecar container in the Cloud Run service (see
# deploy/scanner-service.yaml), reachable on 127.0.0.1:3310.
FROM python:3.12-slim

WORKDIR /app

# Install dependencies first for better layer caching.
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

# Run as a non-root user.
RUN useradd --create-home --uid 1001 appuser
USER appuser

EXPOSE 8080
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8080"]
