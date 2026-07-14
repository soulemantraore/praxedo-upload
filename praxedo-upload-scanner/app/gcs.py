"""Read the object to scan straight from GCS, as a stream.

The scanner is given a ``gs://bucket/key`` URI and streams the bytes to clamd;
they are never fully buffered in memory. In production the client authenticates
with Application Default Credentials (the scanner's Cloud Run service account,
granted roles/storage.objectViewer). Against the fake-gcs emulator
(STORAGE_EMULATOR_HOST set) an anonymous client is used.
"""

from __future__ import annotations

import os
from typing import BinaryIO
from urllib.parse import urlparse

from google.cloud import storage


class GcsError(RuntimeError):
    """The object could not be read (bad URI, missing object, transport error)."""


def parse_gs_uri(gs_uri: str) -> tuple[str, str]:
    parsed = urlparse(gs_uri)
    key = parsed.path.lstrip("/")
    if parsed.scheme != "gs" or not parsed.netloc or not key:
        raise GcsError(f"invalid gs uri: {gs_uri}")
    return parsed.netloc, key


def build_storage_client() -> storage.Client:
    if os.getenv("STORAGE_EMULATOR_HOST"):
        # fake-gcs emulator: no real credentials, project name is arbitrary.
        from google.auth.credentials import AnonymousCredentials

        return storage.Client(
            project=os.getenv("GCS_PROJECT", "praxedo-local"),
            credentials=AnonymousCredentials(),
        )
    return storage.Client()


class GcsReader:
    def __init__(self, client: storage.Client) -> None:
        self._client = client

    def open(self, gs_uri: str) -> BinaryIO:
        bucket_name, key = parse_gs_uri(gs_uri)
        try:
            blob = self._client.bucket(bucket_name).get_blob(key)
            if blob is None:
                raise GcsError(f"object not found: {gs_uri}")
            return blob.open("rb")
        except GcsError:
            raise
        except Exception as exc:  # transport / auth / emulator errors
            raise GcsError(f"cannot read {gs_uri}: {exc}") from exc


def default_gcs_reader() -> GcsReader:
    return GcsReader(build_storage_client())
