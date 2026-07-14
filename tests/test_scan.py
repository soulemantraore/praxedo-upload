"""Integration test: the ClamAV wrapper against a real clamd.

Opt-in (needs Docker; the clamav image downloads a ~1 GB signature database on
first start, so this can take several minutes). Run only this with:

    pytest -m integration

Skip it (default fast run):

    pytest -m "not integration"

GCS is intentionally out of scope here: we feed bytes straight to the scanner,
so the test proves the clamd wiring, not the object download.
"""

from __future__ import annotations

import io
import time

import pytest

clamd = pytest.importorskip("clamd")

from app.scanner import ClamAvScanner  # noqa: E402

# Standard, harmless antivirus test string. Not real malware.
EICAR = (
    r"X5O!P%@AP[4\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*"
).encode("ascii")

pytestmark = pytest.mark.integration


@pytest.fixture(scope="module")
def clamd_endpoint():
    docker = pytest.importorskip("testcontainers.core.container")
    container = docker.DockerContainer("clamav/clamav-debian:latest").with_exposed_ports(3310)
    container.start()
    try:
        host = container.get_container_host_ip()
        port = int(container.get_exposed_port(3310))
        _wait_until_ready(host, port)
        yield host, port
    finally:
        container.stop()


def _wait_until_ready(host: str, port: int, timeout_s: int = 600) -> None:
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        try:
            if clamd.ClamdNetworkSocket(host, port, timeout=5).ping() == "PONG":
                return
        except (clamd.ClamdError, OSError):
            pass
        time.sleep(5)
    raise RuntimeError("clamd did not become ready in time")


def test_detects_eicar(clamd_endpoint):
    host, port = clamd_endpoint
    scanner = ClamAvScanner(host, port, timeout=60)

    infected, threat = scanner.scan(io.BytesIO(EICAR))

    assert infected is True
    assert threat  # a threat name is always present when infected


def test_passes_clean_content(clamd_endpoint):
    host, port = clamd_endpoint
    scanner = ClamAvScanner(host, port, timeout=60)

    infected, threat = scanner.scan(io.BytesIO(b"just a harmless little file"))

    assert infected is False
    assert threat is None
