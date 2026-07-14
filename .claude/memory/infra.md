# Infra — outillage de déploiement GCP (PR #26 `task/infra-tooling`, backend)

Outillage **prêt mais non exécuté** (aucun `gcloud`/`docker` lancé ; pas de projet GCP/auth disponible côté agent). gcloud + Makefile uniquement — **pas de Terraform** (décision D11). Un vrai déploiement nécessite que l'utilisateur fasse `gcloud auth login` puis `make`.

## Topologie cible (Cloud Run) — 3 services (depuis D15, scanner externalisé)
- **`api`** (public, `X-API-Key` vérifié dans l'app) : sert `/api/**`. Multi-conteneurs (app + sidecar Cloud SQL Auth Proxy).
- **`worker`** (privé, **léger** depuis D15) : **2 conteneurs** — app (profil `gcp`) + **Cloud SQL Auth Proxy** (`localhost:5432`). **Plus de ClamAV** ; `min-instances=0` (scale-to-zero). Reçoit le push Pub/Sub sur `/internal/scan-events`, **appelle** le scanner en HTTP (OIDC) et écrit le verdict.
- **`scanner`** (privé, repo `praxedo-upload-scanner`) : **2 conteneurs** — app FastAPI (`POST /scan {gsUri}` → verdict) + **ClamAV** (`clamav/clamav-debian`, clamd `127.0.0.1:3310`, `min-instances=1` pour garder la base de signatures chaude, 2Gi). `ingress: internal`, aucun binding `allUsers`.
- **Cloud SQL** Postgres, **GCS** (bucket + CORS + notification `OBJECT_FINALIZE`), **Pub/Sub** (topic `scan-requests` + DLQ + souscription push OIDC), **Artifact Registry**, **Secret Manager** (mot de passe DB).

## Décision d'ingénierie clé — sidecar Cloud SQL Auth Proxy
Le code applicatif utilise le **pilote JDBC PostgreSQL standard (TCP)**, pas la SocketFactory Cloud SQL, et on n'a **pas modifié `src/`**. Donc **les deux** services embarquent un sidecar Cloud SQL Auth Proxy (`localhost:5432`) → l'api est aussi multi-conteneurs → déploiement par **YAML Knative + `gcloud run services replace`** (pas `gcloud run deploy` mono-conteneur). Alternative future : ajouter la SocketFactory au backend pour supprimer le sidecar api.

## Fichiers (dépôt backend)
`Dockerfile` (multi-stage Maven/JDK21 → temurin JRE21, non-root, même image pour api+worker, profil choisi au runtime), `deploy/Makefile` (cibles `enable-apis`/`bootstrap`/`infra`/`build-push`(Cloud Build)/`deploy-api`/`deploy-worker`/`pubsub-push`/`deploy` ; `render-worker` résout `SCANNER_URL` et exige que le scanner soit déployé d'abord), `deploy/{api,worker}-service.yaml` (rendus par `envsubst` ; worker sans ClamAV), `deploy/gcs-cors.json`, `.github/workflows/deploy.yml`, `deploy/README.md`. `docker-compose.yml` : ajoute un service `scanner` (build `../praxedo-upload-scanner`, `STORAGE_EMULATOR_HOST`).

## Fichiers (dépôt scanner `praxedo-upload-scanner`, nouveau — D15)
`app/{main,scanner,gcs,config,models}.py` (FastAPI `POST /scan`+`GET /health`, wrapper clamd, download GCS en flux), `tests/` (pytest hermétiques + test intégration clamd opt-in), `Dockerfile` (Python seul, clamd = sidecar), `deploy/{scanner-service.yaml, Makefile}` (service privé ; Makefile crée le SA scanner, build/push, deploy, IAM `objectViewer` + `run.invoker` du worker SA), `README.md`. Contrat gelé : req `{gsUri}` → rép `{infected, engine, threatName}`.

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
