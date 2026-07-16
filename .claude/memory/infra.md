# Infra — outillage de déploiement GCP (PR #26 `task/infra-tooling`, backend)

Outillage gcloud + Makefile uniquement — **pas de Terraform** (décision D11). **✅ Déploiement complet réussi et validé bout-en-bout sur `praxedo-upload-test` (2026-07-15)** : les **4 services** api / worker / scanner / ui sont en ligne et le pipeline upload→scan→verdict fonctionne (test **EICAR → `INFECTED`**, fichier infecté → **403** au download). Détail des écueils rencontrés et de leurs correctifs dans la section **Déploiement réel — pièges rencontrés (2026-07-15)** plus bas (PR #12 backend, PR #14 scanner). Voir aussi **CI/CD — auth WIF** pour l'identité de déploiement.

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

## CI/CD — auth WIF (identité canonique + pièges, 2026-07-15)
Le déploiement GitHub Actions s'authentifie à GCP par **Workload Identity Federation** (pas de clé JSON). **Valeurs canoniques** (à mettre dans les secrets GitHub du monorepo `soulemantraore/praxedo-upload`) :
- `GCP_DEPLOY_SA` = `praxedo-deployer@praxedo-upload-test.iam.gserviceaccount.com` (a `artifactregistry.writer`, `run.admin`, `iam.serviceAccountUser`, `cloudbuild.builds.editor`, `pubsub.admin`, `storage.admin`).
- `GCP_WIF_PROVIDER` = `projects/900523019258/locations/global/workloadIdentityPools/github-pool/providers/github-provider` (condition `assertion.repository_owner=='soulemantraore'` ; mappe `attribute.repository`).
- Le SA `praxedo-deployer` doit avoir un binding `roles/iam.workloadIdentityUser` pour le **principalSet** `…/github-pool/attribute.repository/soulemantraore/praxedo-upload` (le **nom du monorepo**).

**Pièges (dérive post-migration monorepo, tous rencontrés le 2026-07-15) :**
- **Repo Artifact Registry `praxedo`** (docker, `europe-west1`) doit exister *avant* le 1er push (créé le 2026-07-14 ; sinon push refusé « (or it may not exist) »). Le crée `make -C praxedo-upload-backend/deploy bootstrap` (idempotent).
- **`Gaia id not found for email`** au `docker push` = le secret `GCP_DEPLOY_SA` pointe un SA inexistant (souvent projet `praxedo-upload` au lieu de `praxedo-upload-test`).
- **Binding WIF listant les anciens repos** `praxedo-upload-backend`/`-ui` au lieu du monorepo `praxedo-upload` → impersonation refusée. Ajouter le principalSet du monorepo.
- En WIF, l'étape `auth` passe au **vert même si l'identité est cassée** (échange de jeton **paresseux**) : l'échec surgit au 1er appel réel (le `docker push`).
- **Artefacts abandonnés à ignorer** : SA `github-deployment-sa` (aucun binding) et pool `praxedo-github-backend` (aucun provider) — tentatives incomplètes.

**Build d'image — approche unifiée (2026-07-15) :** les **3 composants** font un `docker build`/`docker push` **local** sur le runner, authentifié au registre par **`docker/login-action` + `token_format: access_token`** (jeton WIF explicite). On a **abandonné Cloud Build** (`gcloud builds submit`) pour le backend et le scanner : en tant que SA WIF (non-Viewer du projet), `gcloud builds submit` ne peut pas **streamer** les logs du bucket par défaut et rend un code non-nul *alors que le build réussit* (« This tool can only stream logs if you are Viewer/Owner »). **Ne jamais** utiliser `gcloud auth configure-docker` en CI (helper `docker-credential-gcloud` = source de push anonymes) — toujours `docker/login-action`.

## Frontend — déploiement Cloud Run (PR #19 `task/frontend-deploy`, dépôt praxedo-upload-ui)
- **Pas de nginx** (choix utilisateur) : image Docker **simple** multi-stage (build Vite → service statique par **`serve`** Node, repli SPA `-s`, écoute `$PORT`). Validé en local (`docker build` OK ; conteneur : `/` 200, route profonde → repli index.html 200).
- **CI/CD** `.github/workflows/deploy.yml` : push `main` → typecheck + tests → build image → push Artifact Registry → `gcloud run deploy` (public, port 8080). Auth **WIF** (mêmes conventions que le backend : secrets `GCP_WIF_PROVIDER`/`GCP_DEPLOY_SA`, vars `GCP_PROJECT_ID`/`GCP_REGION`/`ARTIFACT_REPO`).
- **VITE_* sont build-time** (Vite les inline dans le bundle) → passées en `--build-arg` depuis les secrets/vars GitHub. `VITE_API_KEY` finit visible dans le bundle (contrainte SPA public ; auth OAuth = évolution).

## Fichiers .env.example
- Backend : `.env.example` (PR #26) — SPRING_PROFILES_ACTIVE, STORAGE_LOCAL_DIR/PUBLIC_BASE_URL (local), GCS_BUCKET/DB_*/GCP_PROJECT_ID/PUBSUB_SCAN_TOPIC/GOOGLE_APPLICATION_CREDENTIALS (gcp). **Depuis D15** : `CLAMAV_*` remplacés par `SCANNER_URL`/`SCANNER_AUDIENCE`/`SCANNER_OIDC_ENABLED`. `.env` gitignoré.
- Frontend : `.env.example` préexistant (jalon 4), `.env` déjà gitignoré.

## Déploiement réel — pièges rencontrés et correctifs (2026-07-15)
Premier déploiement complet sur `praxedo-upload-test`. Ordre qui marche : **infra une fois par un owner** (SA + bucket + pubsub + `iam` + secret) → **scanner** `make all` → **backend** `make deploy` (+ `pubsub` pour la notif GCS) → `--ingress=all` sur le scanner. Écueils, tous corrigés :
- **Secret Manager — `Permission denied on secret`** : les SA runtime (api/worker) doivent avoir `roles/secretmanager.secretAccessor` sur `praxedo-db-password`. Posé par `make iam` (dans `make infra`), **pas** par `make deploy`. Le `DEPLOY_SA` du CI n'a **pas** le droit d'écrire la policy IAM d'un secret (par design) → provisioning one-shot par un owner. (`deploy/README.md` §Dépannage, PR #12)
- **Flyway sur Supabase — `Found non-empty schema(s) "public" but no schema history table`** : Supabase pré-remplit `public` → Flyway refuse de migrer. Fix : `spring.flyway.baseline-on-migrate=true` + `baseline-version=0` (profil `gcp`) → baseline à 0 **puis** applique V1 (le défaut baseline-version=1 SAUTERAIT V1, laissant l'app sans tables vu `ddl-auto:none`). **1ʳᵉ migration** : préférer le **session pooler `:5432`** (le transaction pooler `:6543` casse les locks de session Flyway). (PR #12)
- **Pooler Supabase — `FATAL: (ENOIDENTIFIER) no tenant identifier`** : `DB_USER` doit être `postgres.<project-ref>` (ici `postgres.tmimrnpsbduweomeewyj`), pas `postgres` nu — le pooler (`:5432` **et** `:6543`) route les tenants par ce suffixe. Garde-fou `check-db-user` (prérequis `deploy-api`/`deploy-worker`). Variable GitHub `DB_USER` déjà correcte. (PR #12)
- **Image scanner arm64** : `docker build` local sur Apple Silicon → arm64, refusé par Cloud Run (`manifest ... must support amd64/linux`). Fix : `docker buildx build --platform linux/amd64 --provenance=false --push` (amd64 en local via QEMU + CI amd64, manifest unique). Défaut `BUCKET` du Makefile scanner était `$(PROJECT_ID)-praxedo-files` (inexistant) → corrigé `praxedo-files`. (PR #14)
- **Agent de service GCS créé paresseusement** : `service-<num>@gs-project-accounts…` n'existe qu'après une 1ʳᵉ opération qui le déclenche → `make pubsub` échoue (`does not exist`). Forcer : `gcloud storage service-agent --project=…` puis relancer.
- **Scanner `ingress=internal` casse worker→scanner** : appel Cloud Run→Cloud Run via l'URL `*.run.app` = trafic **externe** → **403** malgré `run.invoker`. Repassé en `--ingress=all` (scanner reste privé : `run.invoker` + OIDC = seul gardien). Alternative propre prod : Direct VPC egress sur le worker.
- **Pas d'endpoint d'admin pour créer une clé API en `gcp`** : `ApiKeyService.createClient()` n'est appelé qu'au seed du profil `local`. Pour tester/exploiter : insérer une ligne `api_client` (`api_key_hash` = `base64(sha256(rawKey))`). **Gap produit** à combler (endpoint d'admin) pour un vrai livrable.

**Validation e2e** : `POST /api/files` (URL signée V4) → `PUT` GCS → notif `OBJECT_FINALIZE` → Pub/Sub → worker → scanner (ClamAV ~355k sigs) → verdict en base, ~3 s. EICAR → `INFECTED` (`Eicar-Test-Signature`) ; `GET /content` sur infecté → **403** (seul `CLEAN` téléchargeable).

## Doc d'architecture
`praxedo-upload-backend/docs/architecture.html` (PR #27) : schéma visuel autonome (hexagonal, cycle de vie, topologie déploiement, choix). Rendu vérifié au navigateur.

Voir [[frontend-ui]] (piège auto-trigger scan) et [[decisions-archi]] (**D15** scanner externalisé, remplace le sidecar ClamAV de D6 ; topologie 3 services).
