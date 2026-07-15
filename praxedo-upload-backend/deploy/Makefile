# =====================================================================
# praxedo-upload-backend - GCP deployment (Cloud Run, gcloud only)
#
# Topology: api (Cloud Run) + worker (Cloud Run) + scanner (separate repo) +
# Supabase (managed Postgres, external) + GCS + Pub/Sub + Artifact Registry.
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
PROJECT_ID       ?= praxedo-upload-test
REGION           ?= europe-west1
REPO             ?= praxedo
IMAGE            ?= praxedo-upload-backend
IMAGE_TAG        ?= latest

# ---- Database: Supabase (managed Postgres, provisioned OUTSIDE GCP) ----
# DB_URL is composed from DB_HOST/DB_PORT/DB_NAME. Credentials stay OUT of the URL:
# Spring reads them separately (datasource.username=DB_USER, datasource.password=
# DB_PASSWORD); DB_USER is a plain env var and DB_PASSWORD comes from Secret Manager
# (secret name = DB_SECRET_NAME).
# NOTE: :6543 is the Supabase transaction pooler (PgBouncer); it lacks some Flyway
# features. If migrations fail at startup, switch DB_PORT to 5432 (session pooler)
# or use a direct connection.
DB_HOST          ?= aws-0-eu-west-1.pooler.supabase.com
DB_PORT          ?= 6543
DB_NAME          ?= postgres
DB_USER          ?= postgres
DB_SECRET_NAME   ?= praxedo-db-password
DB_URL           ?= jdbc:postgresql://$(DB_HOST):$(DB_PORT)/$(DB_NAME)?sslmode=require

# ---- GCS ----
BUCKET           ?= praxedo-files
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

# ---- Sidecars ----
# None: ClamAV moved to the separate scanner service, and Supabase is reached
# directly over TLS (no Cloud SQL Auth Proxy).

# ---- CI/CD : Workload Identity Federation + deploy service account ----
# Monorepo praxedo-upload : one WIF provider + one deploy SA for all components.
WIF_POOL         ?= github-pool
WIF_PROVIDER     ?= github-provider
DEPLOY_SA_NAME   ?= praxedo-deployer
DEPLOY_SA        := $(DEPLOY_SA_NAME)@$(PROJECT_ID).iam.gserviceaccount.com
# GitHub org/user allowed to authenticate, and the repo bound to the deploy SA.
GITHUB_OWNER     ?= soulemantraore
GITHUB_REPOS     ?= soulemantraore/praxedo-upload
# Helpers to iterate over the comma-separated repo list inside recipes.
comma            := ,
empty            :=
space            := $(empty) $(empty)

# ---- Derived (deterministic; no gcloud call needed) ----
IMAGE_URI            := $(REGION)-docker.pkg.dev/$(PROJECT_ID)/$(REPO)/$(IMAGE):$(IMAGE_TAG)

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
        infra build-push check-db-user deploy-api deploy-worker \
        pubsub-push deploy config cicd-setup cicd-values cicd-github

# ---------------------------------------------------------------------
help: ## Show this help.
	@echo "praxedo-upload-backend - GCP deployment targets:"
	@echo ""
	@echo "  enable-apis      Enable the required Google Cloud APIs"
	@echo "  bootstrap        Create the Artifact Registry Docker repository"
	@echo "  infra            GCS(+CORS+notification) + Pub/Sub + SAs + IAM + secrets (DB = Supabase, externe)"
	@echo "  build-push       Build the image with Cloud Build and push to Artifact Registry"
	@echo "  deploy-api       Deploy the api Cloud Run service (gcloud run deploy)"
	@echo "  deploy-worker    Deploy the worker Cloud Run service (gcloud run deploy; calls the scanner)"
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
	@echo "DB_URL              = $(DB_URL)"
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
	  pubsub.googleapis.com \
	  storage.googleapis.com \
	  secretmanager.googleapis.com \
	  iam.googleapis.com \
	  iamcredentials.googleapis.com \
	  --project=$(PROJECT_ID)

# ---------------------------------------------------------------------
bootstrap: ## Create the Artifact Registry Docker repository (idempotent).
	@gcloud artifacts repositories describe $(REPO) \
	  --location=$(REGION) --project=$(PROJECT_ID) >/dev/null 2>&1 \
	  || gcloud artifacts repositories create $(REPO) \
	       --repository-format=docker \
	       --location=$(REGION) \
	       --description="praxedo upload backend images" \
	       --project=$(PROJECT_ID)

# ---------------------------------------------------------------------
# infra sub-targets are run in order to guarantee dependencies.
infra: ## Provision all infrastructure (idempotent-friendly).
	$(MAKE) service-accounts
	$(MAKE) bucket
	$(MAKE) pubsub
	$(MAKE) iam

service-accounts: ## Create the api / worker / push service accounts (idempotent).
	@gcloud iam service-accounts describe $(API_SA) --project=$(PROJECT_ID) >/dev/null 2>&1 \
	  || gcloud iam service-accounts create $(API_SA_NAME) \
	       --display-name="praxedo api runtime" --project=$(PROJECT_ID)
	@gcloud iam service-accounts describe $(WORKER_SA) --project=$(PROJECT_ID) >/dev/null 2>&1 \
	  || gcloud iam service-accounts create $(WORKER_SA_NAME) \
	       --display-name="praxedo worker runtime" --project=$(PROJECT_ID)
	@gcloud iam service-accounts describe $(PUSH_SA) --project=$(PROJECT_ID) >/dev/null 2>&1 \
	  || gcloud iam service-accounts create $(PUSH_SA_NAME) \
	       --display-name="praxedo pubsub push (OIDC)" --project=$(PROJECT_ID)

secrets: ## Store the Supabase DB password in Secret Manager (pass DB_PASSWORD=... from the Supabase dashboard).
	@test -n "$$DB_PASSWORD" || { echo "ERROR: set DB_PASSWORD to your Supabase database password (Project Settings > Database)"; exit 1; }
	@gcloud secrets describe $(DB_SECRET_NAME) --project=$(PROJECT_ID) >/dev/null 2>&1 \
	  || gcloud secrets create $(DB_SECRET_NAME) \
	       --replication-policy=automatic --project=$(PROJECT_ID)
	@printf '%s' "$$DB_PASSWORD" | gcloud secrets versions add $(DB_SECRET_NAME) \
	   --data-file=- --project=$(PROJECT_ID); \
	 echo "Supabase DB password stored in Secret Manager secret '$(DB_SECRET_NAME)' (latest)."

# Supabase is provisioned outside GCP (dashboard) -> no "sql" target here:
# store the password with "make secrets" and set DB_URL.

bucket: ## Create the GCS bucket and apply the CORS policy for browser signed URLs (idempotent).
	@gcloud storage buckets describe gs://$(BUCKET) --project=$(PROJECT_ID) >/dev/null 2>&1 \
	  || gcloud storage buckets create gs://$(BUCKET) \
	       --location=$(REGION) \
	       --uniform-bucket-level-access \
	       --project=$(PROJECT_ID)
	sed 's|https://your-ui-origin.example.com|$(UI_ORIGIN)|g' \
	  $(DEPLOY_DIR)/gcs-cors.json > $(DEPLOY_DIR)/.gcs-cors.rendered.json
	gcloud storage buckets update gs://$(BUCKET) \
	  --cors-file=$(DEPLOY_DIR)/.gcs-cors.rendered.json --project=$(PROJECT_ID)

pubsub: ## Create the scan topic + DLQ and wire the GCS OBJECT_FINALIZE notification (idempotent).
	@gcloud pubsub topics describe $(TOPIC) --project=$(PROJECT_ID) >/dev/null 2>&1 \
	  || gcloud pubsub topics create $(TOPIC) --project=$(PROJECT_ID)
	@gcloud pubsub topics describe $(DLQ) --project=$(PROJECT_ID) >/dev/null 2>&1 \
	  || gcloud pubsub topics create $(DLQ) --project=$(PROJECT_ID)
	# Allow the GCS service agent to publish object-finalize notifications.
	gcloud pubsub topics add-iam-policy-binding $(TOPIC) \
	  --member="serviceAccount:$(GCS_AGENT)" \
	  --role=roles/pubsub.publisher --project=$(PROJECT_ID)
	# Notify the scan topic when an object is finalized (uploaded) in the bucket.
	# (list|grep guard: notifications create is NOT idempotent -- it piles up duplicates.)
	@gcloud storage buckets notifications list gs://$(BUCKET) --project=$(PROJECT_ID) 2>/dev/null \
	  | grep -q "topics/$(TOPIC)" \
	  || gcloud storage buckets notifications create gs://$(BUCKET) \
	       --topic=$(TOPIC) \
	       --event-types=OBJECT_FINALIZE \
	       --payload-format=json \
	       --project=$(PROJECT_ID)

iam: ## Bind least-privilege IAM roles for both runtime service accounts.
	# --- api: sign V4 URLs (signBlob on itself), publish scans, object metadata,
	#     read the DB secret (Supabase reached over TLS, no Cloud SQL IAM).
	gcloud iam service-accounts add-iam-policy-binding $(API_SA) \
	  --member="serviceAccount:$(API_SA)" \
	  --role=roles/iam.serviceAccountTokenCreator --project=$(PROJECT_ID)
	gcloud storage buckets add-iam-policy-binding gs://$(BUCKET) \
	  --member="serviceAccount:$(API_SA)" \
	  --role=roles/storage.objectAdmin --project=$(PROJECT_ID)
	gcloud pubsub topics add-iam-policy-binding $(TOPIC) \
	  --member="serviceAccount:$(API_SA)" \
	  --role=roles/pubsub.publisher --project=$(PROJECT_ID)
	gcloud secrets add-iam-policy-binding $(DB_SECRET_NAME) \
	  --member="serviceAccount:$(API_SA)" \
	  --role=roles/secretmanager.secretAccessor --project=$(PROJECT_ID)

	# --- worker: read/delete objects, read the DB secret (Supabase over TLS).
	gcloud storage buckets add-iam-policy-binding gs://$(BUCKET) \
	  --member="serviceAccount:$(WORKER_SA)" \
	  --role=roles/storage.objectAdmin --project=$(PROJECT_ID)
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
# Guard (prereq of both deploys): the Supabase pooler (aws-*.pooler.supabase.com)
# routes tenants by the "postgres.<project-ref>" username. A bare DB_USER (no
# ".<ref>" suffix) is rejected at connect time with
#   FATAL: (ENOIDENTIFIER) no tenant identifier provided
# which only surfaces as a Cloud Run "container failed to start". Fail fast here.
check-db-user:
	@if echo "$(DB_HOST)" | grep -q 'pooler\.supabase' && ! echo "$(DB_USER)" | grep -q '\.'; then \
	  echo "ERROR: DB_HOST=$(DB_HOST) is the Supabase pooler, but DB_USER=$(DB_USER) has no tenant suffix."; \
	  echo "       Pass DB_USER=postgres.<project-ref>, e.g. make deploy DB_USER=postgres.abcdefgh...";  \
	  exit 1; \
	fi

# ---------------------------------------------------------------------
# Both services are single-container (DB reached over TLS, no sidecar), so they
# deploy imperatively with `gcloud run deploy` -- no Knative YAML to render.
# The env values carry no commas, so the default comma-delimited --set-env-vars
# is safe. Cloud Run adds a default TCP startup probe on --port; we keep an
# explicit one to preserve the generous 300s startup window (Spring Boot boot).
deploy-api: check-db-user ## Deploy the api Cloud Run service (public, X-API-Key enforced in-app).
	# --allow-unauthenticated makes the endpoint public (grants allUsers the
	# run.invoker role); the app still enforces the X-API-Key header itself.
	gcloud run deploy $(API_SERVICE) \
	  --image=$(IMAGE_URI) --region=$(REGION) --project=$(PROJECT_ID) \
	  --service-account=$(API_SA) \
	  --allow-unauthenticated --ingress=all \
	  --port=8080 --cpu=1 --memory=512Mi \
	  --min-instances=0 --max-instances=10 --timeout=60 \
	  --labels=app=praxedo-upload,component=api \
	  --startup-probe=tcpSocket.port=8080,periodSeconds=10,failureThreshold=30 \
	  --set-env-vars=SPRING_PROFILES_ACTIVE=gcp,SERVER_PORT=8080,GCP_PROJECT_ID=$(PROJECT_ID),GCS_BUCKET=$(BUCKET),PUBSUB_SCAN_TOPIC=$(TOPIC),DB_URL=$(DB_URL),DB_USER=$(DB_USER) \
	  --set-secrets=DB_PASSWORD=$(DB_SECRET_NAME):latest

deploy-worker: check-db-user ## Deploy the worker Cloud Run service (calls the private scanner).
	# The worker resolves SCANNER_URL from the deployed scanner: fail early if it
	# is missing. Private service (--no-allow-unauthenticated); only the push SA
	# is later granted run.invoker, by `make pubsub-push`.
	@SU="$(SCANNER_URL)"; test -n "$$SU" || { echo "ERROR: scanner not deployed: $(SCANNER_SERVICE) has no URL. Deploy the praxedo-upload-scanner repo first."; exit 1; }; \
	gcloud run deploy $(WORKER_SERVICE) \
	  --image=$(IMAGE_URI) --region=$(REGION) --project=$(PROJECT_ID) \
	  --service-account=$(WORKER_SA) \
	  --no-allow-unauthenticated --ingress=all \
	  --port=8080 --cpu=1 --memory=1Gi \
	  --min-instances=0 --max-instances=5 --timeout=600 \
	  --labels=app=praxedo-upload,component=worker \
	  --startup-probe=tcpSocket.port=8080,periodSeconds=10,failureThreshold=30 \
	  --set-env-vars=SPRING_PROFILES_ACTIVE=gcp,SERVER_PORT=8080,GCP_PROJECT_ID=$(PROJECT_ID),GCS_BUCKET=$(BUCKET),PUBSUB_SCAN_TOPIC=$(TOPIC),SCANNER_URL=$$SU,SCANNER_AUDIENCE=$$SU,SCANNER_OIDC_ENABLED=true,DB_URL=$(DB_URL),DB_USER=$(DB_USER) \
	  --set-secrets=DB_PASSWORD=$(DB_SECRET_NAME):latest

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
# NOTE: `deploy` deliberately does NOT touch IAM -- it assumes infra is already
# provisioned (`make infra`, which runs `make iam`), done ONCE per project by an
# owner. The CI deploy SA has no rights to set IAM policy on secrets/buckets, by
# design. If a fresh project fails deploy-api/deploy-worker with
#   "Permission denied on secret ... roles/secretmanager.secretAccessor"
# the runtime SAs are missing the secret binding -> run `make iam PROJECT_ID=...`.
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
	@gcloud iam service-accounts describe $(DEPLOY_SA) --project=$(PROJECT_ID) >/dev/null 2>&1 \
	  || gcloud iam service-accounts create $(DEPLOY_SA_NAME) \
	       --display-name="praxedo GitHub Actions deployer" --project=$(PROJECT_ID)
	# 2) Deployment roles (union of what `make deploy` needs, backend + frontend).
	for role in roles/run.admin roles/artifactregistry.writer roles/cloudbuild.builds.editor \
	            roles/iam.serviceAccountUser roles/pubsub.admin roles/storage.admin; do \
	  gcloud projects add-iam-policy-binding $(PROJECT_ID) \
	    --member="serviceAccount:$(DEPLOY_SA)" --role=$$role --condition=None >/dev/null; \
	done
	# 3) Workload Identity pool + GitHub OIDC provider (restricted to the owner).
	@gcloud iam workload-identity-pools describe $(WIF_POOL) \
	  --location=global --project=$(PROJECT_ID) >/dev/null 2>&1 \
	  || gcloud iam workload-identity-pools create $(WIF_POOL) \
	       --location=global --display-name="GitHub Actions" --project=$(PROJECT_ID)
	@gcloud iam workload-identity-pools providers describe $(WIF_PROVIDER) \
	  --location=global --workload-identity-pool=$(WIF_POOL) --project=$(PROJECT_ID) >/dev/null 2>&1 \
	  || gcloud iam workload-identity-pools providers create-oidc $(WIF_PROVIDER) \
	       --location=global --workload-identity-pool=$(WIF_POOL) --display-name="GitHub OIDC" \
	       --issuer-uri="https://token.actions.githubusercontent.com" \
	       --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.repository_owner=assertion.repository_owner" \
	       --attribute-condition="assertion.repository_owner=='$(GITHUB_OWNER)'" \
	       --project=$(PROJECT_ID)
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
