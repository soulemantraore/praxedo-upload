"""Shared test doubles: an in-memory GCS reader and a scriptable scanner."""

from __future__ import annotations

import io

from app.scanner import ScanError


class FakeReader:
    """Stands in for GcsReader: returns bytes (or raises) without touching GCS."""

    def __init__(self, data: bytes = b"", error: str | None = None) -> None:
        self._data = data
        self._error = error
        self.opened_uri: str | None = None

    def open(self, gs_uri: str):
        from app.gcs import GcsError

        self.opened_uri = gs_uri
        if self._error is not None:
            raise GcsError(self._error)
        return io.BytesIO(self._data)


class FakeScanner:
    """Stands in for ClamAvScanner with a scripted verdict."""

    def __init__(
        self,
        verdict: tuple[bool, str | None] = (False, None),
        pong: bool = True,
        error: str | None = None,
    ) -> None:
        self._verdict = verdict
        self._pong = pong
        self._error = error
        self.scanned: bytes | None = None

    def ping(self) -> bool:
        return self._pong

    def scan(self, stream) -> tuple[bool, str | None]:
        self.scanned = stream.read()
        if self._error is not None:
            raise ScanError(self._error)
        return self._verdict
