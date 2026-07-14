"""Request/response contract shared with the Spring worker (RemoteScannerClient).

Contract (frozen):
    request : {"gsUri": "gs://bucket/key"}
    response: {"infected": bool, "engine": str, "threatName": str | null}
"""

from __future__ import annotations

from pydantic import BaseModel, field_validator


class ScanRequest(BaseModel):
    gsUri: str

    @field_validator("gsUri")
    @classmethod
    def _must_be_gs_uri(cls, value: str) -> str:
        if not value.startswith("gs://") or len(value) <= len("gs://"):
            raise ValueError("gsUri must be a non-empty gs:// URI")
        return value


class ScanResponse(BaseModel):
    infected: bool
    engine: str
    threatName: str | None = None
