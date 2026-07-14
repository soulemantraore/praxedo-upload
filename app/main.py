"""FastAPI application exposing the scanner over HTTP.

Endpoints:
    POST /scan   {"gsUri": "..."} -> {"infected", "engine", "threatName"}
    GET  /health                  -> 200 if clamd answers PING/PONG, else 503

A technical failure (object unreadable, clamd unreachable/ERROR) returns HTTP 502
so the caller records SCAN_FAILED. It is never reported as a clean verdict.

The scanner and GCS reader are injected as FastAPI dependencies so tests can
override them without a real clamd or bucket.
"""

from __future__ import annotations

import logging

from fastapi import Depends, FastAPI, HTTPException

from app.config import load_settings
from app.gcs import GcsError, GcsReader, default_gcs_reader
from app.models import ScanRequest, ScanResponse
from app.scanner import ENGINE, ClamAvScanner, ScanError

log = logging.getLogger("scanner")

app = FastAPI(title="praxedo-upload-scanner", version="1.0.0")


def get_scanner() -> ClamAvScanner:
    settings = load_settings()
    return ClamAvScanner(settings.clamd_host, settings.clamd_port, settings.clamd_timeout)


def get_reader() -> GcsReader:
    return default_gcs_reader()


@app.get("/health")
def health(scanner: ClamAvScanner = Depends(get_scanner)) -> dict[str, str]:
    if not scanner.ping():
        raise HTTPException(status_code=503, detail="clamd unavailable")
    return {"status": "ok"}


@app.post("/scan", response_model=ScanResponse)
def scan(
    request: ScanRequest,
    scanner: ClamAvScanner = Depends(get_scanner),
    reader: GcsReader = Depends(get_reader),
) -> ScanResponse:
    try:
        with reader.open(request.gsUri) as stream:
            infected, threat_name = scanner.scan(stream)
    except (GcsError, ScanError) as exc:
        log.warning("scan technical failure for %s: %s", request.gsUri, exc)
        raise HTTPException(status_code=502, detail=str(exc)) from exc

    return ScanResponse(infected=infected, engine=ENGINE, threatName=threat_name)
