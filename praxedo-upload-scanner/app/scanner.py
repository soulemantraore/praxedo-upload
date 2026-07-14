"""ClamAV wrapper: scans a byte stream via the clamd INSTREAM protocol.

Mirrors the semantics the Spring backend used to implement itself:
    OK    -> clean verdict
    FOUND -> infected verdict (with the threat name)
    ERROR -> technical failure (ScanError), NOT a verdict.

A technical failure must never be reported as "clean": the caller turns any
error into SCAN_FAILED, never CLEAN. That invariant is what keeps an infected
file from ever being served.
"""

from __future__ import annotations

from typing import BinaryIO

import clamd

ENGINE = "clamav"


class ScanError(RuntimeError):
    """Technical failure of the scan (engine unreachable, timeout, ERROR)."""


class ClamAvScanner:
    def __init__(self, host: str, port: int, timeout: float) -> None:
        self._host = host
        self._port = port
        self._timeout = timeout

    def _client(self) -> "clamd.ClamdNetworkSocket":
        # One short-lived socket per operation (matches the previous Java client).
        return clamd.ClamdNetworkSocket(self._host, self._port, self._timeout)

    def ping(self) -> bool:
        try:
            return self._client().ping() == "PONG"
        except (clamd.ClamdError, OSError):
            return False

    def scan(self, stream: BinaryIO) -> tuple[bool, str | None]:
        """Return (infected, threat_name). Raise ScanError on technical failure."""
        try:
            result = self._client().instream(stream)
        except (clamd.ClamdError, OSError) as exc:
            raise ScanError(f"clamd communication failed: {exc}") from exc

        status, detail = result["stream"]
        if status == "OK":
            return False, None
        if status == "FOUND":
            return True, detail
        raise ScanError(f"clamd returned ERROR: {detail}")
