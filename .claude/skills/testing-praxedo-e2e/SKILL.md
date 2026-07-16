---
name: testing-praxedo-e2e
description: Use when verifying the deployed praxedo-upload app (UI + API + worker + scanner on Cloud Run, GCP project praxedo-upload-test) works end-to-end — after a deploy, or when the dashboard shows 500s, files are stuck in "Erreur"/"En attente", the metrics look wrong, or an upload/download fails. Drives the deployed UI in the browser and cross-checks gcloud logs.
---

# Testing Praxedo end-to-end (deployed)

## Overview

Verify the full chain on the **deployed** stack: browser → UI → API → GCS →
Pub/Sub → worker → scanner → back to the UI. The golden path is **upload a file
from the front, watch it become "Validé", download it, and confirm the metrics
match the listed files**. When a step fails, the browser shows the symptom and
**gcloud logs show which layer broke** — always confirm the layer in the logs
before touching code.

**Core principle:** a symptom seen in the browser is only half the evidence.
Pair it with the matching service log to locate the failing layer, then fix the
root cause (see the troubleshooting table).

## Coordinates (source of truth: `praxedo-upload-backend/deploy/Makefile`)

- GCP project `praxedo-upload-test`, region `europe-west1`.
- Cloud Run services: `praxedo-api` (public), `praxedo-worker` (private, Pub/Sub push), `praxedo-scanner` (private, ClamAV).
- UI: `https://praxedo-ui-900523019258.europe-west1.run.app/`
- API: `https://praxedo-api-900523019258.europe-west1.run.app`
- GCS bucket: `gs://praxedo-files` ; DB: Supabase Postgres via transaction pooler.
- Auth: browser calls carry `X-API-Key`; worker→scanner is OIDC. File statuses: PENDING→SCANNING→CLEAN/INFECTED (UI: En attente / En analyse / Validé / Bloqué; a technical scan failure shows "Erreur").

**Prerequisites:** `gcloud` authenticated with read access to `praxedo-upload-test`
(logs + `run.services.get`); the MCP Chrome browser connected. Direct `curl` on
`/api/*` needs the `X-API-Key` (baked into the deployed UI bundle as `VITE_API_KEY`,
not needed when driving the UI) — prefer driving the UI so the key stays out of scope.

## Procedure

1. **Load the UI, confirm the dashboard renders.** Open the UI URL, then read
   network + console. `GET /api/files/stats` **and** `GET /api/files?...` must be
   **200**. A 500 on either means a backend/DB problem — go to logs (step 5).
2. **Scan the service logs for fresh errors** (before and during the test):
   `praxedo-api`, then `praxedo-worker`, then `praxedo-scanner` (see Quick
   reference). Note any `severity>=ERROR`.
3. **Upload a file from the front.** Click "Déposer un fichier". The browser
   `file_upload` tool no longer accepts host paths, so inject a File via JS into
   the dialog's `input[type=file]` (see Upload technique). The file appears at the
   top as **En attente** and Total increments.
4. **Watch the scan complete automatically.** Within seconds the worker consumes
   the Pub/Sub push and calls the scanner. Confirm in logs (queries below —
   note the CLEAN verdict is an **INFO** line, so do NOT filter `severity>=ERROR`):
   - scanner: `"POST /scan HTTP/1.1" 200 OK`
   - worker: `FileScanService ... <id> CLEAN` (or `INFECTED` for an EICAR file)
   The `<id>` is the file's UUID from the `GET /api/files` response body. Reload
   the UI: the file flips to **Validé** and **Validés** increments. Upload the
   EICAR string as a second file to prove blocking → **Bloqué**.
5. **Verify metrics match the listed files.** The four cards come from
   `GET /api/files/stats`; the list from `GET /api/files` is **paginated** (default
   `size=6`, see the "N sur M fichier(s) — Page x/y" pager). Total = M = number of
   files; Validés = CLEAN; Bloqués = INFECTED; En analyse = SCANNING. Files in
   PENDING/ERROR are legitimately not in the three status cards (only Total counts
   them), so `Validés + En analyse + Bloqués` need not equal Total.
6. **Verify download.** Click the download icon on a "Validé" file. The UI calls
   `GET /api/files/{id}/content` → **200 JSON `{url}`**, then navigates to the GCS
   signed URL (has `response-content-disposition=attachment`). There must be **no
   CORS error**. A one-off **503** from `storage.googleapis.com` is usually a
   transient GCS blip — re-click, or `curl` the signed URL to confirm it serves 200.

## Upload technique (browser)

`file_upload` rejects filesystem paths; drive the hidden input directly. With the
deposit dialog open:

```js
const input = document.querySelector('input[type=file]');
const file = new File(["clean content\n"], "e2e.txt", { type: "text/plain" });
const dt = new DataTransfer();
dt.items.add(file);
input.files = dt.files;
input.dispatchEvent(new Event('change', { bubbles: true })); // triggers React onChange → register + PUT
```

`input.files.length` may read back `0` right after (React consumes it) — that's
fine, the upload still fires. Confirm via the row appearing in the list.

For an INFECTED path, use the EICAR test string as the content. **The backslash
MUST be doubled in the JS literal** (`\P` is otherwise swallowed and it stops being
EICAR — the file would come back "Validé" and silently break the blocking test):

```js
const eicar = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";
const f = new File([eicar], "eicar.txt", { type: "text/plain" });
const dt = new DataTransfer(); dt.items.add(f);
input.files = dt.files; input.dispatchEvent(new Event('change', { bubbles: true }));
```

## Quick reference (gcloud logs)

Use `--freshness=1h` (portable, avoids brittle timestamp math on macOS).

```bash
# Errors on a service (repeat with service_name = praxedo-worker / praxedo-scanner)
gcloud logging read 'resource.type="cloud_run_revision" AND resource.labels.service_name="praxedo-api" AND severity>=ERROR' \
  --project=praxedo-upload-test --freshness=1h --limit=10 --format="value(timestamp, textPayload)" --order=desc
# The SQL/stack detail lives in textPayload of app log lines, not the ERROR access log — grep textPayload for "SQLState|Exception|/scan".

# Scan verdict (CLEAN is INFO, so NO severity filter — filter on the log text instead)
gcloud logging read 'resource.type="cloud_run_revision" AND resource.labels.service_name="praxedo-worker" AND textPayload:"FileScanService"' \
  --project=praxedo-upload-test --freshness=1h --limit=20 --format="value(timestamp, textPayload)" --order=desc

# Did the scan reach the scanner container? (only /health = request never arrived → 404 at the GFE)
gcloud logging read 'resource.type="cloud_run_revision" AND resource.labels.service_name="praxedo-scanner"' \
  --project=praxedo-upload-test --freshness=1h --limit=15 --format="value(timestamp, textPayload)" --order=desc

# Is a code fix actually live? Compare serving revision / env vs the merge time
gcloud run services describe praxedo-worker --region=europe-west1 --project=praxedo-upload-test --format="value(spec.template.spec.containers[0].env)"
```

## Troubleshooting — symptom → layer → root cause (all seen in real runs)

| Browser symptom | Log signature | Root cause & fix |
|---|---|---|
| Every UI fetch `TypeError: Failed to fetch` | API reachable (no-cors opaque), preflight `OPTIONS` 403 no ACAO | Backend had no CORS. Enable `.cors()` + `CorsConfigurationSource`, inject `APP_CORS_ALLOWED_ORIGINS`. |
| Download preflight blocked at `storage.googleapis.com` | GFE preflight, no ACAO | Bucket CORS still on placeholder origin. `gcloud storage buckets update gs://praxedo-files --cors-file=praxedo-upload-backend/deploy/gcs-cors.json --project=praxedo-upload-test` (origin **without** trailing slash). |
| Download preflight, `X-API-Key` in request headers | — | Old flow: `/content` 302 → fetch leaks `X-API-Key` to GCS. Fix: `/content` returns JSON `{url}`, UI navigates (no fetch). |
| `500` on `/api/files/stats` intermittently | `SQLState: 42P05 ... prepared statement "S_1" already exists` | Supabase transaction pooler + server prepared statements. Set pgjdbc `prepareThreshold=0` (Hikari data-source-properties, gcp profile). |
| `500` on `/api/files` (list) | `SQLState: 42883 ... function lower(bytea) does not exist` | Null search param untyped without server prepared statements. `cast(:q as string)` in the JPQL search query. |
| All files end in "Erreur" | worker `appel du scanner en echec: 404`; scanner logs only `/health` | Scanner `ingress=internal` + worker has no VPC egress → GFE 404. `gcloud run services update praxedo-scanner --ingress=all --region=europe-west1 --project=praxedo-upload-test` (still IAM/OIDC-protected). Loosens a network control → confirm with the user first. |
| File stuck "En attente" forever | no scan request in worker logs for it | Scan never enqueued (uploaded while pipeline broken). No UI rescan for PENDING and `ReconciliationService` has no trigger — orphan. Manual cleanup only: `DELETE FROM file WHERE id='<id>'` (Supabase SQL editor) + `gcloud storage rm` its object. Deletion is the user's action, not the agent's. |
| Any 500 whose SQLState/stack is **not** in this table | some other `textPayload` in the api log | Not catalogued — read the full `textPayload` around it, identify the layer, and do NOT default to "CORS" (CORS never causes a 500). Investigate, don't guess. |

## Common mistakes

- **Reading only the ERROR access log.** The real cause (SQLState, stack, `/scan`
  404 body) is in `textPayload` of the app log lines around it — widen the query.
- **Blaming CORS for a scan/DB error.** CORS only blocks the browser from *reading*
  a response; the request still reached the backend. A 500 body you can't read is
  a backend bug, not CORS.
- **Assuming a code fix is live.** Check the serving revision timestamp vs the
  merge; a fix only applies after its deploy workflow completes.
- **Rescanning a PENDING file via the UI** — there is no button; only ERROR/CLEAN
  files expose "Relancer l'analyse".
- **Non-ASCII / `===` in shell commands** — the command validator blocks them; keep
  commands ASCII. For Maven use `JAVA_HOME=/opt/homebrew/opt/openjdk@21/... mvn`.
