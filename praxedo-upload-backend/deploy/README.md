# Déploiement GCP — praxedo-upload-backend

Outillage de déploiement du micro-service sur **Google Cloud Run** (gcloud + Makefile, sans Terraform).

Une seule image Docker (backend) est construite et déployée en **deux services Cloud Run** (api vs
worker, choisis au runtime par le profil Spring `gcp`). Le **scan** vit dans un **troisième service**,
`praxedo-scanner`, construit depuis le dépôt séparé `praxedo-upload-scanner` (Python + ClamAV).

## Topologie (3 services)

```
   Client / UI ──PUT (URL signée)──▶  GCS bucket (fichiers)
       │                              - CORS PUT/GET (origine UI)
       │  X-API-Key /api/**           - notification OBJECT_FINALIZE ──┐
       ▼                                                               ▼
┌──────────────────┐   publish (rescan)                    Pub/Sub topic (scan-requests)
│  Cloud Run: api  │────────────────────────┐                         │ push (OIDC)
│  (public)        │                        ▼                         ▼
│  - app           │              ┌─────────────────────────────────────────────┐
│                  │              │  Cloud Run: worker (privé, scale-to-zero)    │
│                  │              │  - app  POST /internal/scan-events           │
└────────┬─────────┘              └──────┬────────────────────────────┬─────────┘
         │                               │ HTTP POST /scan {gsUri}     │
         │ JDBC / TLS                    │ (OIDC, run.invoker)         │ JDBC / TLS
         │            ┌──────────────────▼─────────────────────┐       │
         │            │  Cloud Run: scanner (privé)             │       │
         │            │  - app FastAPI  → { infected, ... }     │       │
         │            │  - clamav sidecar (clamd:3310)          │──reads──▶ GCS
         │            │    min-instances=1 (signatures chaudes) │       │
         │            └─────────────────────────────────────────┘       │
         ▼                                                              ▼
   ┌────────────────────  Supabase (PostgreSQL managé, externe à GCP)  ──────────────┐
   │  JDBC + Flyway (migrations au démarrage), sslmode=require                        │
   └─────────────────────────────────────────────────────────────────────────────────┘

Dead-letter : Pub/Sub topic scan-requests-dlq (après MAX_ATTEMPTS livraisons).
Secret Manager : mot de passe DB (praxedo-db-password).
Artifact Registry : dépôt Docker des images (backend + scanner).
```

- **`api`** (public) : sert `/api/**`. L'authentification se fait **dans l'application** via l'en-tête
  `X-API-Key` ; le service Cloud Run est ouvert (`allUsers` → `run.invoker`). Génère les URLs signées V4
  (upload/download), publie les demandes de rescan sur Pub/Sub, lit/écrit les métadonnées dans **Supabase**.
- **`worker`** (privé, **léger**) : reçoit le push Pub/Sub sur `POST /internal/scan-events`, **appelle le
  scanner** en HTTP (`POST /scan {gsUri}`, jeton OIDC) et écrit le verdict renvoyé dans **Supabase**. Plus de
  ClamAV ni de proxy DB ici → `min-instances=0` (scale-to-zero), **1 conteneur** (app seul).
- **`scanner`** (privé, dépôt `praxedo-upload-scanner`) : `POST /scan {gsUri}` → lit l'objet depuis GCS →
  ClamAV → renvoie `{ infected, engine, threatName }`. `min-instances=1` (~2Gi) car ClamAV charge sa base
  de signatures. **N'écrit jamais en base** ; seul le worker le fait (voir ADR **D15**).

Le même topic `scan-requests` transporte **deux** formes de message, toutes deux gérées par
`/internal/scan-events` : la notification GCS `OBJECT_FINALIZE` (attribut `objectId` = storageKey,
auto-trigger après upload) et le message applicatif de rescan (`data` = fileId en base64).

### Base de données : Supabase (Postgres managé, externe à GCP)

L'application utilise le pilote JDBC PostgreSQL standard (TCP uniquement) et **le code source n'est pas
modifié** : elle se connecte **directement** à Supabase via la connection string `DB_URL`
(`jdbc:postgresql://<projet>.pooler.supabase.com:5432/postgres?sslmode=require`). Plus de **Cloud SQL Auth
Proxy** en sidecar ni de rôle `cloudsql.client` : la base est provisionnée **hors GCP** (dashboard Supabase),
et le mot de passe est stocké dans **Secret Manager** (`make secrets DB_PASSWORD=...`). Les services restent
décrits en **YAML Knative** (`api-service.yaml`, `worker-service.yaml`, appliqués via
`gcloud run services replace`) pour porter la configuration d'environnement.

> **Flyway** : utiliser le **session pooler** Supabase (`:5432`) ou une connexion **directe** pour les
> migrations ; le **transaction pooler** (`:6543`) ne supporte pas toutes les fonctionnalités Flyway.

## Fichiers

| Fichier | Rôle |
|---|---|
| `../Dockerfile` | Image multi-stage (build Maven/JDK 21 → runtime JRE 21), fat jar, port 8080. |
| `Makefile` | Toutes les cibles gcloud paramétrées par variables (voir en tête du fichier). |
| `api-service.yaml` | Manifeste Cloud Run du service `api` (app seul). Template `${...}` rendu par envsubst. |
| `worker-service.yaml` | Manifeste Cloud Run du service `worker` (app seul ; appelle le scanner). |
| `gcs-cors.json` | Politique CORS du bucket (PUT/GET depuis l'origine de l'UI). |
| *(dépôt `praxedo-upload-scanner`)* | Le service `scanner` a son propre `deploy/` (manifeste + Makefile). |
| `../.github/workflows/deploy.yml` | CI/CD : test + build + `make deploy` sur push `main` (WIF). |

## Prérequis

- Un **projet GCP** avec la **facturation activée**.
- `gcloud` installé et connecté : `gcloud auth login` puis `gcloud config set project <PROJECT_ID>`.
- `make`, `envsubst` (paquet `gettext`) et `openssl` disponibles localement.
- Les images de build/déploiement s'exécutent côté GCP (Cloud Build) : **Docker n'est pas requis en local**.

## De zéro à déployé

Renseignez au minimum `PROJECT_ID`, `REGION`, `UI_ORIGIN` (soit en éditant le haut du `Makefile`,
soit en les passant à chaque commande). Exemple avec surcharges en ligne :

```bash
cd deploy

# Variables réutilisées ci-dessous
export PROJECT_ID=my-gcp-project
export REGION=europe-west1
export UI_ORIGIN=https://app.example.com

# 1) Activer les APIs (une fois)
make enable-apis PROJECT_ID=$PROJECT_ID

# 2) Créer le dépôt Artifact Registry (une fois)
make bootstrap PROJECT_ID=$PROJECT_ID REGION=$REGION

# 3) Provisionner l'infra GCP (GCS+CORS+notification, Pub/Sub topic+DLQ,
#    comptes de service, IAM, secret du mot de passe DB). La base Supabase est
#    provisionnée HORS GCP (dashboard Supabase). Renseigner aussi DB_URL (connection
#    string Supabase) au moment du déploiement des services (make deploy).
#    DB_PASSWORD = mot de passe Supabase, REQUIS (stocké dans Secret Manager).
DB_PASSWORD='mot-de-passe-supabase' \
  make infra PROJECT_ID=$PROJECT_ID REGION=$REGION UI_ORIGIN=$UI_ORIGIN

# 4) Déployer le SCANNER d'abord (dépôt praxedo-upload-scanner), car le worker
#    résout SCANNER_URL depuis le service scanner déployé.
( cd ../../praxedo-upload-scanner/deploy && \
  make all PROJECT_ID=$PROJECT_ID REGION=$REGION )   # scanner-sa + build-push + deploy + iam

# 5) Construire l'image backend, déployer api + worker, câbler la souscription push
make deploy PROJECT_ID=$PROJECT_ID REGION=$REGION UI_ORIGIN=$UI_ORIGIN
```

`make deploy` enchaîne `build-push` → `deploy-api` → `deploy-worker` → `pubsub-push`.
`deploy-worker` **résout `SCANNER_URL`** via `gcloud run services describe praxedo-scanner` et échoue
explicitement si le scanner n'est pas encore déployé (étape 4). La souscription push est créée **après**
le worker car son `push-endpoint` est l'URL du worker.

> **Ordre inter-dépôts** : `make infra` (crée le SA `praxedo-worker`) → **scanner** `make all` (le
> `iam` du scanner accorde `run.invoker` au SA du worker) → **backend** `make deploy`.

Vérifier la configuration résolue à tout moment : `make config PROJECT_ID=$PROJECT_ID`.

## CI/CD — Workload Identity Federation (GitHub Actions)

Les workflows des **deux** dépôts (backend + frontend) se déploient sur Cloud Run via
**Workload Identity Federation** — aucune clé de compte de service n'est stockée dans GitHub.
Trois cibles automatisent la mise en place, à lancer **une seule fois** (elles couvrent les deux
dépôts car ils partagent le même provider et le même compte de déploiement) :

```bash
# 1) Créer le compte de déploiement + le provider WIF et autoriser les dépôts GitHub
make cicd-setup PROJECT_ID=$PROJECT_ID \
  GITHUB_OWNER=mon-org \
  GITHUB_REPOS=mon-org/praxedo-upload-backend,mon-org/praxedo-upload-ui

# 2a) Afficher les deux valeurs à coller dans GitHub (secrets des deux dépôts)
make cicd-values PROJECT_ID=$PROJECT_ID

# 2b) …ou les pousser directement via le CLI gh (nécessite `gh auth login`)
make cicd-github PROJECT_ID=$PROJECT_ID
```

`cicd-setup` crée le compte `praxedo-deployer`, lui accorde les rôles de déploiement
(`run.admin`, `artifactregistry.writer`, `cloudbuild.builds.editor`, `iam.serviceAccountUser`,
`pubsub.admin`, `storage.admin`), crée le pool + provider OIDC GitHub (restreint à `GITHUB_OWNER`),
et autorise chaque dépôt à usurper le compte. `cicd-values`/`cicd-github` renseignent les deux
**secrets** `GCP_WIF_PROVIDER` et `GCP_DEPLOY_SA` ; les **variables** restantes (`GCP_PROJECT_ID`,
`GCP_REGION`, `GCS_BUCKET`, `UI_ORIGIN` côté backend ; `VITE_API_BASE_URL`, `ARTIFACT_REPO`,
`VITE_API_KEY`… côté frontend) se renseignent dans chaque dépôt — voir l'en-tête des workflows.

## Sécurité de l'endpoint push (production)

`/internal/scan-events` n'est **pas** authentifié dans l'application. En production, il est protégé par le
**jeton OIDC de la souscription push** : la souscription porte le service account `praxedo-pubsub-push`,
seul membre autorisé (`roles/run.invoker`) sur le service worker (qui n'a **pas** de binding `allUsers`).
Cloud Run valide le jeton avant de router la requête. L'agent de service Pub/Sub reçoit
`roles/iam.serviceAccountTokenCreator` pour émettre ce jeton.

## Clés API

Les clés API des clients (`X-API-Key`) sont stockées **hachées en base** (table `api_clients`, pilotée par
l'application), pas en variable d'environnement. À l'échelle, on peut aussi les gérer via Secret Manager
et un endpoint d'administration ; hors périmètre de cet outillage.

## Notes d'exploitation

- **Flyway** s'exécute au démarrage des deux services (même image, profil `gcp`). Les migrations prennent un
  verrou côté Postgres : des démarrages concurrents sont sûrs (l'un attend l'autre).
- **Coût** : le **worker** est désormais léger (`min-instances=0`, 1 vCPU / 1Gi = app seul, plus de proxy DB) et
  scale à zéro. La charge « chaude » (base de signatures ClamAV) est portée par le **scanner**
  (`min-instances=1` + `cpu-throttling=false`, ~2Gi), ajustable dans son `scanner-service.yaml`.
- **CI/CD** : le workflow suppose l'infra déjà provisionnée (étapes 1–3 ci-dessus faites une fois à la main) ;
  chaque push `main` ne fait que tester, builder et redéployer (`make deploy`). Secrets/variables requis :
  voir l'en-tête de `../.github/workflows/deploy.yml`.
```
