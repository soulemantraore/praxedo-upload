# =====================================================================
# praxedo-upload-backend - GCP deployment (Cloud Run, gcloud only)
#
# Topology: api (Cloud Run) + worker (Cloud Run, app + ClamAV + Cloud SQL
# proxy sidecars) + Cloud SQL (Postgres) + GCS + Pub/Sub + Artifact Registry.
#
# Override any variable on the command line, e.g.:
#   make deploy PROJECT_ID=my-proj REGION=europe-west1 IMAGE_TAG=$(git rev-parse --short HEAD)
#
# Order from zero to deployed (see deploy/README.md):
#   make enable-apis
#   make bootstrap
#   DB_PASSWORD=... make infra        # DB_PASSWORD optional; auto-generated if unset
#   make deploy                       # build-push + deploy-api + deploy-worker + pubsub-push
# =====================================================================

SHELL := /bin/bash

# ---- Project / registry ----
PROJECT_ID       ?= my-gcp-project
REGION           ?= europe-west1
REPO             ?= praxedo
IMAGE            ?= praxedo-upload-backend
IMAGE_TAG        ?= latest

# ---- Cloud SQL (Postgres) ----
DB_INSTANCE      ?= praxedo-sql
DB_TIER          ?= db-custom-1-3840
DB_VERSION       ?= POSTGRES_16
DB_NAME          ?= praxedo
DB_USER          ?= praxedo
DB_SECRET_NAME   ?= praxedo-db-password

# ---- GCS ----
BUCKET           ?= $(PROJECT_ID)-praxedo-files
UI_ORIGIN        ?= https://your-ui-origin.example.com

# ---- Pub/Sub ----
TOPIC            ?= scan-requests
DLQ              ?= scan-requests-dlq
SUBSCRIPTION     ?= scan-worker-sub
ACK_DEADLINE     ?= 600
MAX_ATTEMPTS     ?= 5

# ---- Cloud Run services ----
API_SERVICE      ?= praxedo-api
WORKER_SERVICE   ?= praxedo-worker
# The private scanner service (deployed from the praxedo-upload-scanner repo).
SCANNER_SERVICE  ?= praxedo-scanner

# ---- Service accounts ----
API_SA_NAME      ?= praxedo-api
WORKER_SA_NAME   ?= praxedo-worker
PUSH_SA_NAME     ?= praxedo-pubsub-push
API_SA           := $(API_SA_NAME)@$(PROJECT_ID).iam.gserviceaccount.com
WORKER_SA        := $(WORKER_SA_NAME)@$(PROJECT_ID).iam.gserviceaccount.com
PUSH_SA          := $(PUSH_SA_NAME)@$(PROJECT_ID).iam.gserviceaccount.com

# ---- Sidecar images ----
# ClamAV now lives in the separate scanner service, not the worker.
PROXY_IMAGE      ?= gcr.io/cloud-sql-connectors/cloud-sql-proxy:latest

# ---- CI/CD : Workload Identity Federation + deploy service account ----
# Shared by BOTH repos (backend + frontend) : one WIF provider, one deploy SA.
WIF_POOL         ?= github-pool
WIF_PROVIDER     ?= github-provider
DEPLOY_SA_NAME   ?= praxedo-deployer
DEPLOY_SA        := $(DEPLOY_SA_NAME)@$(PROJECT_ID).iam.gserviceaccount.com
# GitHub org/user allowed to authenticate, and the repos bound to the deploy SA.
GITHUB_OWNER     ?= soulemantraore
GITHUB_REPOS     ?= soulemantraore/praxedo-upload-backend,soulemantraore/praxedo-upload-ui
# Helpers to iterate over the comma-separated repo list inside recipes.
comma            := ,
empty            :=
space            := $(empty) $(empty)

# ---- Derived (deterministic; no gcloud call needed) ----
IMAGE_URI            := $(REGION)-docker.pkg.dev/$(PROJECT_ID)/$(REPO)/$(IMAGE):$(IMAGE_TAG)
SQL_CONNECTION_NAME  := $(PROJECT_ID):$(REGION):$(DB_INSTANCE)

# ---- Derived (resolved lazily, only inside recipes that need them) ----
PROJECT_NUMBER  = $(shell gcloud projects describe $(PROJECT_ID) --format='value(projectNumber)')
PUBSUB_AGENT    = service-$(PROJECT_NUMBER)@gcp-sa-pubsub.iam.gserviceaccount.com
GCS_AGENT       = service-$(PROJECT_NUMBER)@gs-project-accounts.iam.gserviceaccount.com
WORKER_URL      = $(shell gcloud run services describe $(WORKER_SERVICE) --region=$(REGION) --project=$(PROJECT_ID) --format='value(status.url)')
# URL of the private scanner service (must be deployed first, from the scanner repo).
SCANNER_URL     = $(shell gcloud run services describe $(SCANNER_SERVICE) --region=$(REGION) --project=$(PROJECT_ID) --format='value(status.url)')

# Path of this Makefile / the deploy directory (works from any CWD).
DEPLOY_DIR := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))
REPO_ROOT  := $(abspath $(DEPLOY_DIR)/..)

.DEFAULT_GOAL := help

.PHONY: help enable-apis bootstrap service-accounts secrets sql bucket pubsub iam \
        infra build-push render-api render-worker deploy-api deploy-worker \
        pubsub-push deploy config cicd-setup cicd-values cicd-github

# ---------------------------------------------------------------------
help: ## Show this help.
	@echo "praxedo-upload-backend - GCP deployment targets:"
	@echo ""
	@echo "  enable-apis      Enable the required Google Cloud APIs"
	@echo "  bootstrap        Create the Artifact Registry Docker repository"
	@echo "  infra            Cloud SQL + GCS(+CORS+notification) + Pub/Sub + SAs + IAM + secrets"
	@echo "  build-push       Build the image with Cloud Build and push to Artifact Registry"
	@echo "  deploy-api       Render + apply the api Cloud Run service"
	@echo "  deploy-worker    Render + apply the worker Cloud Run service (app + Cloud SQL proxy; calls the scanner)"
	@echo "  pubsub-push      Create the authenticated push subscription to the worker"
	@echo "  deploy           build-push + deploy-api + deploy-worker + pubsub-push"
	@echo ""
	@echo "  NOTE: deploy the praxedo-upload-scanner repo BEFORE deploy-worker"
	@echo "        (the worker resolves SCANNER_URL from the deployed scanner service)."
	@echo "  config           Print the resolved configuration"
	@echo ""
	@echo "CI/CD (GitHub Actions -> Cloud Run), run once:"
	@echo "  cicd-setup       Create the deploy SA + WIF provider, bind the GitHub repos"
	@echo "  cicd-values      Print GCP_WIF_PROVIDER and GCP_DEPLOY_SA (to paste in GitHub)"
	@echo "  cicd-github      Push those two secrets to both repos via the gh CLI"
	@echo ""
	@echo "Override variables, e.g.: make deploy PROJECT_ID=my-proj REGION=europe-west1"

config: ## Print the resolved configuration.
	@echo "PROJECT_ID          = $(PROJECT_ID)"
	@echo "REGION              = $(REGION)"
	@echo "IMAGE_URI           = $(IMAGE_URI)"
	@echo "SQL_CONNECTION_NAME = $(SQL_CONNECTION_NAME)"
	@echo "BUCKET              = $(BUCKET)"
	@echo "TOPIC / DLQ / SUB   = $(TOPIC) / $(DLQ) / $(SUBSCRIPTION)"
	@echo "API_SERVICE         = $(API_SERVICE)  (SA: $(API_SA))"
	@echo "WORKER_SERVICE      = $(WORKER_SERVICE)  (SA: $(WORKER_SA))"
	@echo "PUSH_SA             = $(PUSH_SA)"

# ---------------------------------------------------------------------
enable-apis: ## Enable required Google Cloud APIs.
	gcloud services enable \
	  run.googleapis.com \
	  cloudbuild.googleapis.com \
	  artifactregistry.googleapis.com \
	  sqladmin.googleapis.com \
	  pubsub.googleapis.com \
	  storage.googleapis.com \
	  secretmanager.googleapis.com \
	  iam.googleapis.com \
	  iamcredentials.googleapis.com \
	  --project=$(PROJECT_ID)

# ---------------------------------------------------------------------
bootstrap: ## Create the Artifact Registry Docker repository.
	gcloud artifacts repositories create $(REPO) \
	  --repository-format=docker \
	  --location=$(REGION) \
	  --description="praxedo upload backend images" \
	  --project=$(PROJECT_ID) || true

# ---------------------------------------------------------------------
# infra sub-targets are run in order to guarantee dependencies.
infra: ## Provision all infrastructure (idempotent-friendly).
	$(MAKE) service-accounts
	$(MAKE) secrets
	$(MAKE) sql
	$(MAKE) bucket
	$(MAKE) pubsub
	$(MAKE) iam

service-accounts: ## Create the api / worker / push service accounts.
	gcloud iam service-accounts create $(API_SA_NAME) \
	  --display-name="praxedo api runtime" --project=$(PROJECT_ID) || true
	gcloud iam service-accounts create $(WORKER_SA_NAME) \
	  --display-name="praxedo worker runtime" --project=$(PROJECT_ID) || true
	gcloud iam service-accounts create $(PUSH_SA_NAME) \
	  --display-name="praxedo pubsub push (OIDC)" --project=$(PROJECT_ID) || true

secrets: ## Create the DB password secret (value from DB_PASSWORD or auto-generated).
	gcloud secrets create $(DB_SECRET_NAME) \
	  --replication-policy=automatic --project=$(PROJECT_ID) || true
	@PW="$${DB_PASSWORD:-$$(openssl rand -base64 24)}"; \
	 printf '%s' "$$PW" | gcloud secrets versions add $(DB_SECRET_NAME) \
	   --data-file=- --project=$(PROJECT_ID); \
	 echo "DB password stored in Secret Manager secret '$(DB_SECRET_NAME)' (latest)."

sql: ## Create the Cloud SQL instance, database and user.
	gcloud sql instances create $(DB_INSTANCE) \
	  --database-version=$(DB_VERSION) \
	  --tier=$(DB_TIER) \
	  --region=$(REGION) \
	  --project=$(PROJECT_ID) || true
	gcloud sql databases create $(DB_NAME) \
	  --instance=$(DB_INSTANCE) --project=$(PROJECT_ID) || true
	gcloud sql users create $(DB_USER) \
	  --instance=$(DB_INSTANCE) \
	  --password="$$(gcloud secrets versions access latest --secret=$(DB_SECRET_NAME) --project=$(PROJECT_ID))" \
	  --project=$(PROJECT_ID) || true

bucket: ## Create the GCS bucket and apply the CORS policy for browser signed URLs.
	gcloud storage buckets create gs://$(BUCKET) \
	  --location=$(REGION) \
	  --uniform-bucket-level-access \
	  --project=$(PROJECT_ID) || true
	sed 's|https://your-ui-origin.example.com|$(UI_ORIGIN)|g' \
	  $(DEPLOY_DIR)/gcs-cors.json > $(DEPLOY_DIR)/.gcs-cors.rendered.json
	gcloud storage buckets update gs://$(BUCKET) \
	  --cors-file=$(DEPLOY_DIR)/.gcs-cors.rendered.json --project=$(PROJECT_ID)

pubsub: ## Create the scan topic + DLQ and wire the GCS OBJECT_FINALIZE notification.
	gcloud pubsub topics create $(TOPIC) --project=$(PROJECT_ID) || true
	gcloud pubsub topics create $(DLQ) --project=$(PROJECT_ID) || true
	# Allow the GCS service agent to publish object-finalize notifications.
	gcloud pubsub topics add-iam-policy-binding $(TOPIC) \
	  --member="serviceAccount:$(GCS_AGENT)" \
	  --role=roles/pubsub.publisher --project=$(PROJECT_ID)
	# Notify the scan topic when an object is finalized (uploaded) in the bucket.
	gcloud storage buckets notifications create gs://$(BUCKET) \
	  --topic=$(TOPIC) \
	  --event-types=OBJECT_FINALIZE \
	  --payload-format=json \
	  --project=$(PROJECT_ID) || true

iam: ## Bind least-privilege IAM roles for both runtime service accounts.
	# --- api: sign V4 URLs (signBlob on itself), publish scans, object metadata,
	#     Cloud SQL, read the DB secret.
	gcloud iam service-accounts add-iam-policy-binding $(API_SA) \
	  --member="serviceAccount:$(API_SA)" \
	  --role=roles/iam.serviceAccountTokenCreator --project=$(PROJECT_ID)
	gcloud storage buckets add-iam-policy-binding gs://$(BUCKET) \
	  --member="serviceAccount:$(API_SA)" \
	  --role=roles/storage.objectAdmin --project=$(PROJECT_ID)
	gcloud pubsub topics add-iam-policy-binding $(TOPIC) \
	  --member="serviceAccount:$(API_SA)" \
	  --role=roles/pubsub.publisher --project=$(PROJECT_ID)
	gcloud projects add-iam-policy-binding $(PROJECT_ID) \
	  --member="serviceAccount:$(API_SA)" --role=roles/cloudsql.client
	gcloud secrets add-iam-policy-binding $(DB_SECRET_NAME) \
	  --member="serviceAccount:$(API_SA)" \
	  --role=roles/secretmanager.secretAccessor --project=$(PROJECT_ID)
	# --- worker: read/delete objects, Cloud SQL, read the DB secret.
	gcloud storage buckets add-iam-policy-binding gs://$(BUCKET) \
	  --member="serviceAccount:$(WORKER_SA)" \
	  --role=roles/storage.objectAdmin --project=$(PROJECT_ID)
	gcloud projects add-iam-policy-binding $(PROJECT_ID) \
	  --member="serviceAccount:$(WORKER_SA)" --role=roles/cloudsql.client
	gcloud secrets add-iam-policy-binding $(DB_SECRET_NAME) \
	  --member="serviceAccount:$(WORKER_SA)" \
	  --role=roles/secretmanager.secretAccessor --project=$(PROJECT_ID)
	# --- Pub/Sub service agent: mint OIDC tokens as the push SA and publish to the DLQ.
	gcloud iam service-accounts add-iam-policy-binding $(PUSH_SA) \
	  --member="serviceAccount:$(PUBSUB_AGENT)" \
	  --role=roles/iam.serviceAccountTokenCreator --project=$(PROJECT_ID)
	gcloud pubsub topics add-iam-policy-binding $(DLQ) \
	  --member="serviceAccount:$(PUBSUB_AGENT)" \
	  --role=roles/pubsub.publisher --project=$(PROJECT_ID)

# ---------------------------------------------------------------------
build-push: ## Build the image with Cloud Build and push to Artifact Registry.
	gcloud builds submit $(REPO_ROOT) --tag=$(IMAGE_URI) --project=$(PROJECT_ID)
	# Local alternative (needs Docker + one-time auth):
	#   gcloud auth configure-docker $(REGION)-docker.pkg.dev
	#   docker build -t $(IMAGE_URI) $(REPO_ROOT)
	#   docker push $(IMAGE_URI)

# ---------------------------------------------------------------------
# Render the Knative templates with envsubst (explicit variable allow-list so
# nothing else in the YAML is accidentally expanded).
render-api:
	IMAGE_URI="$(IMAGE_URI)" API_SERVICE="$(API_SERVICE)" API_SA="$(API_SA)" \
	GCP_PROJECT_ID="$(PROJECT_ID)" GCS_BUCKET="$(BUCKET)" PUBSUB_SCAN_TOPIC="$(TOPIC)" \
	DB_NAME="$(DB_NAME)" DB_USER="$(DB_USER)" DB_SECRET_NAME="$(DB_SECRET_NAME)" \
	SQL_CONNECTION_NAME="$(SQL_CONNECTION_NAME)" PROXY_IMAGE="$(PROXY_IMAGE)" \
	  envsubst '$$IMAGE_URI $$API_SERVICE $$API_SA $$GCP_PROJECT_ID $$GCS_BUCKET $$PUBSUB_SCAN_TOPIC $$DB_NAME $$DB_USER $$DB_SECRET_NAME $$SQL_CONNECTION_NAME $$PROXY_IMAGE' \
	  < $(DEPLOY_DIR)/api-service.yaml > $(DEPLOY_DIR)/.api-service.rendered.yaml

render-worker:
	@SU="$(SCANNER_URL)"; test -n "$$SU" || { echo "ERROR: scanner not deployed: $(SCANNER_SERVICE) has no URL. Deploy the praxedo-upload-scanner repo first."; exit 1; }; \
	IMAGE_URI="$(IMAGE_URI)" WORKER_SERVICE="$(WORKER_SERVICE)" WORKER_SA="$(WORKER_SA)" \
	GCP_PROJECT_ID="$(PROJECT_ID)" GCS_BUCKET="$(BUCKET)" PUBSUB_SCAN_TOPIC="$(TOPIC)" \
	DB_NAME="$(DB_NAME)" DB_USER="$(DB_USER)" DB_SECRET_NAME="$(DB_SECRET_NAME)" \
	SQL_CONNECTION_NAME="$(SQL_CONNECTION_NAME)" PROXY_IMAGE="$(PROXY_IMAGE)" \
	SCANNER_URL="$$SU" SCANNER_AUDIENCE="$$SU" \
	  envsubst '$$IMAGE_URI $$WORKER_SERVICE $$WORKER_SA $$GCP_PROJECT_ID $$GCS_BUCKET $$PUBSUB_SCAN_TOPIC $$DB_NAME $$DB_USER $$DB_SECRET_NAME $$SQL_CONNECTION_NAME $$PROXY_IMAGE $$SCANNER_URL $$SCANNER_AUDIENCE' \
	  < $(DEPLOY_DIR)/worker-service.yaml > $(DEPLOY_DIR)/.worker-service.rendered.yaml

deploy-api: render-api ## Deploy the api Cloud Run service (public, X-API-Key enforced in-app).
	gcloud run services replace $(DEPLOY_DIR)/.api-service.rendered.yaml \
	  --region=$(REGION) --project=$(PROJECT_ID)
	# Public endpoint: the app checks X-API-Key itself.
	gcloud run services add-iam-policy-binding $(API_SERVICE) \
	  --region=$(REGION) --member=allUsers \
	  --role=roles/run.invoker --project=$(PROJECT_ID)

deploy-worker: render-worker ## Deploy the worker Cloud Run service (app + Cloud SQL proxy; calls the private scanner).
	gcloud run services replace $(DEPLOY_DIR)/.worker-service.rendered.yaml \
	  --region=$(REGION) --project=$(PROJECT_ID)

# ---------------------------------------------------------------------
pubsub-push: ## Create/update the authenticated push subscription to the worker.
	# Only the push SA may invoke the (otherwise private) worker.
	gcloud run services add-iam-policy-binding $(WORKER_SERVICE) \
	  --region=$(REGION) \
	  --member="serviceAccount:$(PUSH_SA)" \
	  --role=roles/run.invoker --project=$(PROJECT_ID)
	# Create the subscription, or update the push endpoint if it already exists.
	gcloud pubsub subscriptions create $(SUBSCRIPTION) \
	  --topic=$(TOPIC) \
	  --push-endpoint="$(WORKER_URL)/internal/scan-events" \
	  --push-auth-service-account=$(PUSH_SA) \
	  --ack-deadline=$(ACK_DEADLINE) \
	  --dead-letter-topic=$(DLQ) \
	  --max-delivery-attempts=$(MAX_ATTEMPTS) \
	  --project=$(PROJECT_ID) \
	|| gcloud pubsub subscriptions update $(SUBSCRIPTION) \
	  --push-endpoint="$(WORKER_URL)/internal/scan-events" \
	  --push-auth-service-account=$(PUSH_SA) \
	  --project=$(PROJECT_ID)
	# Dead-letter delivery needs the Pub/Sub agent to subscribe on this subscription.
	gcloud pubsub subscriptions add-iam-policy-binding $(SUBSCRIPTION) \
	  --member="serviceAccount:$(PUBSUB_AGENT)" \
	  --role=roles/pubsub.subscriber --project=$(PROJECT_ID)

# ---------------------------------------------------------------------
deploy: ## Build + push + deploy both services + wire the push subscription.
	$(MAKE) build-push
	$(MAKE) deploy-api
	$(MAKE) deploy-worker
	$(MAKE) pubsub-push

# ---------------------------------------------------------------------
# CI/CD : Workload Identity Federation for GitHub Actions.
# Run `cicd-setup` ONCE, then `cicd-values` (or `cicd-github`) to fill the two
# secrets (GCP_WIF_PROVIDER, GCP_DEPLOY_SA) in BOTH GitHub repos.
cicd-setup: ## Create the deploy SA + WIF provider and authorize the GitHub repos.
	# 1) Deploy service account, impersonated by GitHub Actions via WIF.
	gcloud iam service-accounts create $(DEPLOY_SA_NAME) \
	  --display-name="praxedo GitHub Actions deployer" --project=$(PROJECT_ID) || true
	# 2) Deployment roles (union of what `make deploy` needs, backend + frontend).
	for role in roles/run.admin roles/artifactregistry.writer roles/cloudbuild.builds.editor \
	            roles/iam.serviceAccountUser roles/pubsub.admin roles/storage.admin; do \
	  gcloud projects add-iam-policy-binding $(PROJECT_ID) \
	    --member="serviceAccount:$(DEPLOY_SA)" --role=$$role --condition=None >/dev/null; \
	done
	# 3) Workload Identity pool + GitHub OIDC provider (restricted to the owner).
	gcloud iam workload-identity-pools create $(WIF_POOL) \
	  --location=global --display-name="GitHub Actions" --project=$(PROJECT_ID) || true
	gcloud iam workload-identity-pools providers create-oidc $(WIF_PROVIDER) \
	  --location=global --workload-identity-pool=$(WIF_POOL) --display-name="GitHub OIDC" \
	  --issuer-uri="https://token.actions.githubusercontent.com" \
	  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.repository_owner=assertion.repository_owner" \
	  --attribute-condition="assertion.repository_owner=='$(GITHUB_OWNER)'" \
	  --project=$(PROJECT_ID) || true
	# 4) Let each GitHub repo impersonate the deploy SA.
	@for repo in $(subst $(comma),$(space),$(GITHUB_REPOS)); do \
	  echo "  authorizing $$repo"; \
	  gcloud iam service-accounts add-iam-policy-binding $(DEPLOY_SA) \
	    --role=roles/iam.workloadIdentityUser \
	    --member="principalSet://iam.googleapis.com/projects/$(PROJECT_NUMBER)/locations/global/workloadIdentityPools/$(WIF_POOL)/attribute.repository/$$repo" \
	    --project=$(PROJECT_ID) >/dev/null; \
	done
	@echo ""
	@$(MAKE) cicd-values

cicd-values: ## Print GCP_WIF_PROVIDER and GCP_DEPLOY_SA (to paste in GitHub secrets).
	@echo "Paste in GitHub > Settings > Secrets and variables > Actions (both repos):"
	@echo "  GCP_WIF_PROVIDER = projects/$(PROJECT_NUMBER)/locations/global/workloadIdentityPools/$(WIF_POOL)/providers/$(WIF_PROVIDER)"
	@echo "  GCP_DEPLOY_SA    = $(DEPLOY_SA)"

cicd-github: ## Push the two secrets to both repos via the gh CLI (needs `gh auth login`).
	@for repo in $(subst $(comma),$(space),$(GITHUB_REPOS)); do \
	  echo "  setting secrets on $$repo"; \
	  gh secret set GCP_WIF_PROVIDER --repo $$repo \
	    --body "projects/$(PROJECT_NUMBER)/locations/global/workloadIdentityPools/$(WIF_POOL)/providers/$(WIF_PROVIDER)"; \
	  gh secret set GCP_DEPLOY_SA --repo $$repo --body "$(DEPLOY_SA)"; \
	done
