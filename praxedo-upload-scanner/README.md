# praxedo-upload-scanner

Micro-service de scan antivirus, **fournisseur externe minimal** du système
`praxedo-upload`. Il enveloppe **ClamAV** derrière une petite API HTTP : on lui
donne l'URI d'un objet GCS, il le télécharge en flux, le scanne, et renvoie un
verdict. Il **ne touche jamais** la base de données et n'a **aucun callback** —
c'est le backend Spring qui l'appelle et qui, seul, écrit le verdict.

## API

`POST /scan`
```json
// requête
{ "gsUri": "gs://bucket/owner/id/fichier.pdf" }
// réponse
{ "infected": false, "engine": "clamav", "threatName": null }
```
- Verdict sain : `{"infected": false, "engine": "clamav", "threatName": null}`.
- Verdict infecté : `{"infected": true, "engine": "clamav", "threatName": "Eicar-Test-Signature"}`.
- **Échec technique** (objet illisible, clamd injoignable ou `ERROR`) : **HTTP 502**.
  Jamais un faux « sain » — l'appelant en fait un `SCAN_FAILED`, jamais un `CLEAN`.

`GET /health` → `200` si clamd répond `PING`/`PONG`, sinon `503` (readiness Cloud Run).

## Architecture

```
Spring worker --POST /scan {gsUri} (OIDC)--> [ app (FastAPI) ]  --INSTREAM-->  [ clamav / clamd ]
                                                     |
                                                     +-- lit gs://bucket/key en flux (google-cloud-storage)
```

Deux conteneurs dans un seul service Cloud Run **privé** : l'app Python (cette
image) + un sidecar `clamav/clamav-debian` (clamd sur `127.0.0.1:3310`). La base
de signatures ClamAV (~1 Go) reste chaude grâce à `min-instances=1`.

Le code est découpé par responsabilité : `models` (contrat Pydantic), `gcs`
(téléchargement en flux), `scanner` (protocole clamd), `main` (API FastAPI, DI).

## Développement

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements-dev.txt

pytest                 # tests hermétiques rapides (clamd + GCS simulés)
pytest -m integration  # test avec un vrai clamd (Docker requis, lent : ~1 Go de signatures)
```

Lancer l'API en local (nécessite un clamd joignable, p.ex. via docker-compose du backend) :
```bash
CLAMAV_HOST=127.0.0.1 CLAMAV_PORT=3310 uvicorn app.main:app --port 8000
```

## Configuration (variables d'environnement)

| Variable | Défaut | Rôle |
|---|---|---|
| `CLAMAV_HOST` | `127.0.0.1` | Hôte clamd (sidecar). |
| `CLAMAV_PORT` | `3310` | Port clamd. |
| `CLAMAV_TIMEOUT` | `540` | Timeout socket clamd (s). |
| `STORAGE_EMULATOR_HOST` | — | Si défini, client GCS anonyme (émulateur fake-gcs). |
| `GCS_PROJECT` | `praxedo-local` | Projet GCS pour l'émulateur uniquement. |

En production, l'accès GCS se fait via l'identité du service Cloud Run
(Application Default Credentials + `roles/storage.objectViewer`).

## Déploiement (GCP Cloud Run)

Voir `deploy/` (manifeste Knative + Makefile gcloud). Service **privé** : pas de
binding `allUsers`, seul le compte de service du worker backend reçoit
`run.invoker`. L'`ingress` est `all` (et non `internal`) car le worker Cloud Run
n'a pas de connecteur VPC : il appelle le scanner via son URL publique `run.app`.
La confidentialité reste assurée par IAM/OIDC (`--no-allow-unauthenticated`), pas
par l'ingress — sans jeton valide, toute requête est rejetée (403). Enchaînement :
`make all` (crée le SA,
build+push, deploy, IAM), puis `make url` pour récupérer l'URL à passer au
worker (`SCANNER_URL`).

## Piège connu

- **Gros fichiers** : clamd limite la taille d'un flux INSTREAM
  (`StreamMaxLength`, défaut 25M) et le scan (`MaxFileSize` / `MaxScanSize`).
  Pour des fichiers plus gros, relever ces valeurs dans la configuration du
  sidecar `clamav` (variables d'environnement de l'image officielle ou
  `clamd.conf` monté), sinon clamd répond « INSTREAM size limit exceeded ».
