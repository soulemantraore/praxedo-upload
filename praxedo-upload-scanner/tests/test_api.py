"""Hermetic API tests: clamd and GCS are replaced by in-memory doubles."""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from app.main import app, get_reader, get_scanner
from conftest import FakeReader, FakeScanner


@pytest.fixture
def client():
    yield TestClient(app)
    app.dependency_overrides.clear()


def _use(scanner: FakeScanner, reader: FakeReader) -> None:
    app.dependency_overrides[get_scanner] = lambda: scanner
    app.dependency_overrides[get_reader] = lambda: reader


def test_scan_clean_returns_not_infected(client):
    _use(FakeScanner(verdict=(False, None)), FakeReader(b"harmless"))

    response = client.post("/scan", json={"gsUri": "gs://bucket/owner/id/file.txt"})

    assert response.status_code == 200
    assert response.json() == {"infected": False, "engine": "clamav", "threatName": None}


def test_scan_infected_returns_threat_name(client):
    _use(FakeScanner(verdict=(True, "Eicar-Test-Signature")), FakeReader(b"x"))

    response = client.post("/scan", json={"gsUri": "gs://bucket/key"})

    assert response.status_code == 200
    assert response.json() == {
        "infected": True,
        "engine": "clamav",
        "threatName": "Eicar-Test-Signature",
    }


def test_scan_passes_object_bytes_to_the_engine(client):
    scanner = FakeScanner()
    reader = FakeReader(b"the-actual-bytes")
    _use(scanner, reader)

    client.post("/scan", json={"gsUri": "gs://bucket/key"})

    assert reader.opened_uri == "gs://bucket/key"
    assert scanner.scanned == b"the-actual-bytes"


def test_scan_engine_error_is_502_not_a_clean_verdict(client):
    _use(FakeScanner(error="clamd returned ERROR: boom"), FakeReader(b"x"))

    response = client.post("/scan", json={"gsUri": "gs://bucket/key"})

    assert response.status_code == 502


def test_scan_unreadable_object_is_502(client):
    _use(FakeScanner(), FakeReader(error="object not found"))

    response = client.post("/scan", json={"gsUri": "gs://bucket/missing"})

    assert response.status_code == 502


def test_scan_rejects_non_gs_uri(client):
    _use(FakeScanner(), FakeReader(b"x"))

    response = client.post("/scan", json={"gsUri": "https://evil/x"})

    assert response.status_code == 422


def test_health_ok_when_clamd_answers(client):
    _use(FakeScanner(pong=True), FakeReader())

    assert client.get("/health").status_code == 200


def test_health_503_when_clamd_down(client):
    _use(FakeScanner(pong=False), FakeReader())

    assert client.get("/health").status_code == 503
