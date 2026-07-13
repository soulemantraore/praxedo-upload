# Déploiement GCP — praxedo-upload-backend

Outillage de déploiement du micro-service sur **Google Cloud Run** (gcloud + Makefile, sans Terraform).

Une seule image Docker est construite et déployée en **deux services Cloud Run** ; le comportement
(api vs worker) est choisi au runtime par le profil Spring `gcp`.

## Topologie

```
                       ┌───────────────────────────────────────────────┐
   Client / UI ──PUT──▶│  GCS bucket (fichiers)                         │
       │  (URL signée) │  - CORS: PUT/GET depuis l'origine de l'UI      │
       │               │  - notification OBJECT_FINALIZE ──┐            │
       │               └───────────────────────────────────┼───────────┘
       │                                                    ▼
       │  X-API-Key                                 Pub/Sub topic (scan-requests)
       ▼  /api/**                                          │  push (OIDC)
┌──────────────────┐   publish (rescan)                    ▼
│  Cloud Run: api  │──────────────────────────▶  ┌────────────────────────────────┐
│  - app           │                              │  Cloud Run: worker             │
│  - cloud-sql-    │                              │  - app  /internal/scan-events  │
│    proxy sidecar │                              │  - clamav sidecar (clamd:3310) │
└────────┬─────────┘                              │  - cloud-sql-proxy sidecar     │
         │                                        └──────────┬─────────────────────┘
         │ localhost:5432                                    │ localhost:5432
         ▼                                                   ▼
   ┌───────────────────────────  Cloud SQL (PostgreSQL)  ───────────────────────────┐
   │  JPA + Flyway (migrations au démarrage)                                          │
   └─────────────────────────────────────────────────────────────────────────────────┘

Dead-letter : Pub/Sub topic scan-requests-dlq (après MAX_ATTEMPTS livraisons).
Secret Manager : mot de passe DB (praxedo-db-password).
Artifact Registry : dépôt Docker des images.
```

- **`api`** (public) : sert `/api/**`. L'authentification se fait **dans l'application** via l'en-tête
  `X-API-Key` ; le service Cloud Run est ouvert (`allUsers` → `run.invoker`). Génère les URLs signées V4
  (upload/download), publie les demandes de rescan sur Pub/Sub, lit/écrit les métadonnées en Cloud SQL.
- **`worker`** (privé) : reçoit le push Pub/Sub sur `POST /internal/scan-events`, lit l'objet depuis GCS,
  le scanne via **ClamAV** (sidecar, `localhost:3310`), écrit le verdict en Cloud SQL. `min-instances=1`
  et ~2Gi de mémoire car ClamAV charge sa base de signatures au démarrage.

Le même topic `scan-requests` transporte **deux** formes de message, toutes deux gérées par
`/internal/scan-events` : la notification GCS `OBJECT_FINALIZE` (attribut `objectId` = storageKey,
auto-trigger après upload) et le message applicatif de rescan (`data` = fileId en base64).

### Pourquoi un sidecar Cloud SQL Auth Proxy (et deux manifestes YAML)

L'application utilise le pilote JDBC PostgreSQL standard (TCP uniquement) et **le code source n'est pas
modifié**. Plutôt que d'ajouter la `SocketFactory` Cloud SQL au pom, chaque service embarque le
**Cloud SQL Auth Proxy** en sidecar : il expose Postgres en TCP sur `127.0.0.1:5432`, authentifié par IAM
(`roles/cloudsql.client`). L'app se connecte donc à `jdbc:postgresql://127.0.0.1:5432/<db>` sans changement
de code. Comme les deux services sont multi-conteneurs, ils sont décrits en **YAML Knative**
(`api-service.yaml`, `worker-service.yaml`) et appliqués via `gcloud run services replace`.

## Fichiers

| Fichier | Rôle |
|---|---|
| `../Dockerfile` | Image multi-stage (build Maven/JDK 21 → runtime JRE 21), fat jar, port 8080. |
| `Makefile` | Toutes les cibles gcloud paramétrées par variables (voir en tête du fichier). |
| `api-service.yaml` | Manifeste Cloud Run du service `api` (app + proxy). Template `${...}` rendu par envsubst. |
| `worker-service.yaml` | Manifeste Cloud Run du service `worker` (app + ClamAV + proxy). |
| `gcs-cors.json` | Politique CORS du bucket (PUT/GET depuis l'origine de l'UI). |
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

# 3) Provisionner l'infra (Cloud SQL, GCS+CORS+notification, Pub/Sub topic+DLQ,
#    comptes de service, IAM, secret du mot de passe DB).
#    DB_PASSWORD est optionnel : généré aléatoirement et stocké dans Secret Manager si absent.
DB_PASSWORD='choisir-un-mot-de-passe' \
  make infra PROJECT_ID=$PROJECT_ID REGION=$REGION UI_ORIGIN=$UI_ORIGIN

# 4) Construire l'image, déployer api + worker, câbler la souscription push
make deploy PROJECT_ID=$PROJECT_ID REGION=$REGION UI_ORIGIN=$UI_ORIGIN
```

`make deploy` enchaîne `build-push` → `deploy-api` → `deploy-worker` → `pubsub-push`.
La souscription push est créée **après** le worker car son `push-endpoint` est l'URL du worker.

Vérifier la configuration résolue à tout moment : `make config PROJECT_ID=$PROJECT_ID`.

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
- **Coût du worker** : `min-instances=1` + `cpu-throttling=false` → une instance toujours chaude
  (4 vCPU / 4Gi = somme des conteneurs). Ajustable dans `worker-service.yaml` selon la taille des signatures.
- **CI/CD** : le workflow suppose l'infra déjà provisionnée (étapes 1–3 ci-dessus faites une fois à la main) ;
  chaque push `main` ne fait que tester, builder et redéployer (`make deploy`). Secrets/variables requis :
  voir l'en-tête de `../.github/workflows/deploy.yml`.
```
