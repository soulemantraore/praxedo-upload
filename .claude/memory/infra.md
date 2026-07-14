# Infra — outillage de déploiement GCP (PR #26 `task/infra-tooling`, backend)

Outillage **prêt mais non exécuté** (aucun `gcloud`/`docker` lancé ; pas de projet GCP/auth disponible côté agent). gcloud + Makefile uniquement — **pas de Terraform** (décision D11). Un vrai déploiement nécessite que l'utilisateur fasse `gcloud auth login` puis `make`.

## Topologie cible (Cloud Run) — 3 services (depuis D15, scanner externalisé)
- **`api`** (public, `X-API-Key` vérifié dans l'app) : sert `/api/**`. **Mono-conteneur** (app seul ; DB Supabase en TLS direct depuis D17 → plus de sidecar).
- **`worker`** (privé, **léger** depuis D15) : **mono-conteneur** (app, profil `gcp` ; DB Supabase en TLS direct depuis D17). **Plus de ClamAV** ; `min-instances=0` (scale-to-zero). Reçoit le push Pub/Sub sur `/internal/scan-events`, **appelle** le scanner en HTTP (OIDC) et écrit le verdict.
- **`scanner`** (privé, repo `praxedo-upload-scanner`) : **2 conteneurs** — app FastAPI (`POST /scan {gsUri}` → verdict) + **ClamAV** (`clamav/clamav-debian`, clamd `127.0.0.1:3310`, `min-instances=1` pour garder la base de signatures chaude, 2Gi). `ingress: internal`, aucun binding `allUsers`.
- **Cloud SQL** Postgres, **GCS** (bucket + CORS + notification `OBJECT_FINALIZE`), **Pub/Sub** (topic `scan-requests` + DLQ + souscription push OIDC), **Artifact Registry**, **Secret Manager** (mot de passe DB).

> **Changement DB (D17, 2026-07-14) :** **Supabase Postgres** remplace **Cloud SQL**. Conséquences (à implémenter, non encore faites) : plus de provisioning Cloud SQL ni de **sidecar Cloud SQL Auth Proxy** (la « décision d'ingénierie clé » ci-dessous devient caduque pour la connexion DB → api/worker peuvent redevenir mono-conteneur côté DB) ; l'app se connecte à la **connection string Supabase** (pooler PgBouncer `:6543` en transaction-pooling ou `:5432` en direct, `sslmode=require`) via le pilote JDBC Postgres standard — `src/` inchangé (D7). Flyway : préférer session-pooling/connexion directe (le transaction-pooling PgBouncer ne supporte pas tout). Secret = URL/mot de passe Supabase (Secret Manager). Voir [[decisions-archi]] **D17**.

## Déploiement — `gcloud run deploy` impératif (plus de YAML Knative depuis 2026-07-14)
Historique : tant que la DB passait par un **sidecar Cloud SQL Auth Proxy**, api/worker étaient multi-conteneurs → déploiement par **YAML Knative + `gcloud run services replace`**. Depuis **D17 (Supabase en TLS direct, pilote JDBC standard, `src/` inchangé)**, ce sidecar a disparu : **api et worker sont mono-conteneur**. On a donc supprimé les templates `*-service.yaml` + les cibles `render-*` et on déploie **impérativement** :
- **api / worker** : `gcloud run deploy` mono-conteneur (env/secret/scaling/ingress/sonde en **flags**). api = `--allow-unauthenticated` (remplace le binding `allUsers`) ; worker = `--no-allow-unauthenticated` (seul le push SA reçoit `run.invoker` via `pubsub-push`).
- **scanner** : 2 conteneurs (app FastAPI + sidecar ClamAV) → `gcloud run deploy` **multi-conteneurs** (`--container app … --container clamav …`), avec `--depends-on=clamav`, `--startup-probe`/`--liveness-probe`, `--no-cpu-throttling`, `--min-instances=1`. Nécessite gcloud ≥ ~2024 (flags `--container`/`--depends-on`/`--startup-probe`) — vérifié OK sur SDK 539.
Plus d'`envsubst` ni de `gcloud run services replace` dans aucun composant.

## Fichiers (dépôt backend)
`Dockerfile` (multi-stage Maven/JDK21 → temurin JRE21, non-root, même image pour api+worker, profil choisi au runtime), `deploy/Makefile` (cibles `enable-apis`/`bootstrap`/`infra`/`build-push`(Cloud Build)/`deploy-api`/`deploy-worker`/`pubsub-push`/`deploy` ; `deploy-worker` résout `SCANNER_URL` en tête de recette et exige que le scanner soit déployé d'abord ; plus de cibles `render-*`), `deploy/gcs-cors.json`, `.github/workflows/backend-deploy.yml`, `deploy/README.md`. `docker-compose.yml` : ajoute un service `scanner` (build `../praxedo-upload-scanner`, `STORAGE_EMULATOR_HOST`).

## Fichiers (dépôt scanner `praxedo-upload-scanner`, nouveau — D15)
`app/{main,scanner,gcs,config,models}.py` (FastAPI `POST /scan`+`GET /health`, wrapper clamd, download GCS en flux), `tests/` (pytest hermétiques + test intégration clamd opt-in), `Dockerfile` (Python seul, clamd = sidecar), `deploy/Makefile` (service privé ; crée le SA scanner, build/push, `deploy` = `gcloud run deploy` multi-conteneurs app+clamav, IAM `objectViewer` + `run.invoker` du worker SA ; plus de `scanner-service.yaml`), `README.md`. Contrat gelé : req `{gsUri}` → rép `{infected, engine, threatName}`.

## IAM (moindre privilège) — vérifié cohérent
- **api SA** : `serviceAccountTokenCreator` **sur elle-même** (signature **V4 signBlob** des URLs GCS), `storage.objectAdmin`, `pubsub.publisher`, `cloudsql.client`, accès secret DB.
- **worker SA** : `storage.objectAdmin`, `cloudsql.client`, accès secret DB. **Depuis D15** : `run.invoker` **sur le service scanner** (accordé côté repo scanner). Le worker mint son propre jeton OIDC via le metadata server → aucun rôle supplémentaire pour ça.
- **scanner SA** (`praxedo-scanner`, nouveau) : `roles/storage.objectViewer` sur le bucket (lecture seule ; le worker garde `objectAdmin` pour `delete`). Service privé, pas de binding `allUsers`.
- **push SA** : `run.invoker` sur le worker (worker non public). Agent Pub/Sub : `tokenCreator` sur push SA + `publisher` DLQ + `subscriber`.

## Cohérence env ↔ profil gcp (vérifiée)
Noms d'env du worker YAML = placeholders de `application-gcp.yml` : `GCS_BUCKET`, `DB_URL`/`DB_USER`/`DB_PASSWORD`, `SCANNER_URL`/`SCANNER_AUDIENCE`/`SCANNER_OIDC_ENABLED` (remplacent `CLAMAV_HOST/PORT` depuis D15), `GCP_PROJECT_ID`, `PUBSUB_SCAN_TOPIC`. `SCANNER_URL` résolu par le Makefile (`gcloud run services describe praxedo-scanner`). Topic défaut `scan-requests` aligné.

## À remplir avant déploiement réel
`PROJECT_ID`, `REGION`, `UI_ORIGIN` (min.) ; `DB_PASSWORD` optionnel (auto-généré). CI : secrets `GCP_WIF_PROVIDER`/`GCP_DEPLOY_SA` + variables `GCP_PROJECT_ID`/`GCP_REGION`/`GCS_BUCKET`/`UI_ORIGIN`.

## Frontend — déploiement Cloud Run (PR #19 `task/frontend-deploy`, dépôt praxedo-upload-ui)
- **Pas de nginx** (choix utilisateur) : image Docker **simple** multi-stage (build Vite → service statique par **`serve`** Node, repli SPA `-s`, écoute `$PORT`). Validé en local (`docker build` OK ; conteneur : `/` 200, route profonde → repli index.html 200).
- **CI/CD** `.github/workflows/deploy.yml` : push `main` → typecheck + tests → build image → push Artifact Registry → `gcloud run deploy` (public, port 8080). Auth **WIF** (mêmes conventions que le backend : secrets `GCP_WIF_PROVIDER`/`GCP_DEPLOY_SA`, vars `GCP_PROJECT_ID`/`GCP_REGION`/`ARTIFACT_REPO`).
- **VITE_* sont build-time** (Vite les inline dans le bundle) → passées en `--build-arg` depuis les secrets/vars GitHub. `VITE_API_KEY` finit visible dans le bundle (contrainte SPA public ; auth OAuth = évolution).

## Fichiers .env.example
- Backend : `.env.example` (PR #26) — SPRING_PROFILES_ACTIVE, STORAGE_LOCAL_DIR/PUBLIC_BASE_URL (local), GCS_BUCKET/DB_*/GCP_PROJECT_ID/PUBSUB_SCAN_TOPIC/GOOGLE_APPLICATION_CREDENTIALS (gcp). **Depuis D15** : `CLAMAV_*` remplacés par `SCANNER_URL`/`SCANNER_AUDIENCE`/`SCANNER_OIDC_ENABLED`. `.env` gitignoré.
- Frontend : `.env.example` préexistant (jalon 4), `.env` déjà gitignoré.

## Doc d'architecture
`praxedo-upload-backend/docs/architecture.html` (PR #27) : schéma visuel autonome (hexagonal, cycle de vie, topologie déploiement, choix). Rendu vérifié au navigateur.

Voir [[frontend-ui]] (piège auto-trigger scan) et [[decisions-archi]] (**D15** scanner externalisé, remplace le sidecar ClamAV de D6 ; topologie 3 services).
