"""Runtime configuration, sourced from environment variables.

The scanner is intentionally configuration-light: it only needs to know where
clamd listens. GCS credentials come from Application Default Credentials (the
Cloud Run service account) in production, or from the fake-gcs emulator when
STORAGE_EMULATOR_HOST is set (handled in app.gcs).
"""

from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    clamd_host: str
    clamd_port: int
    clamd_timeout: float


def load_settings() -> Settings:
    """Build settings from the environment on each call (test-friendly)."""
    return Settings(
        clamd_host=os.getenv("CLAMAV_HOST", "127.0.0.1"),
        clamd_port=int(os.getenv("CLAMAV_PORT", "3310")),
        # Generous read timeout: a large file scan can be slow. Must stay below
        # the caller's HTTP read timeout (the Spring worker), which in turn stays
        # below the Pub/Sub push ack deadline.
        clamd_timeout=float(os.getenv("CLAMAV_TIMEOUT", "540")),
    )
