# =====================================================================
# praxedo-upload-scanner - GCP deployment (Cloud Run, gcloud only)
#
# A private Cloud Run service (app + ClamAV sidecar) that the Spring worker
# calls over HTTP with an OIDC token. Shares the Artifact Registry repo and the
# GCS bucket of the backend project.
#
# Order from zero to deployed:
#   make scanner-sa        # once: create the scanner runtime service account
#   make build-push        # build + push the scanner image
#   make deploy            # deploy the private Cloud Run service (app + ClamAV sidecar)
#   make iam               # bucket read for the scanner + run.invoker for the worker
#   make url               # print the deployed scanner URL (feed it to the worker)
#
# Override variables on the command line, e.g.:
#   make deploy PROJECT_ID=my-proj REGION=europe-west1 IMAGE_TAG=$(git rev-parse --short HEAD)
# =====================================================================

SHELL := /bin/bash

# ---- Project / registry (same project + Artifact Registry repo as the backend) ----
PROJECT_ID       ?= praxedo-upload-test
REGION           ?= europe-west1
REPO             ?= praxedo
IMAGE            ?= praxedo-upload-scanner
IMAGE_TAG        ?= latest

# ---- Cloud Run service ----
SCANNER_SERVICE  ?= praxedo-scanner

# ---- Service accounts ----
SCANNER_SA_NAME  ?= praxedo-scanner
SCANNER_SA       := $(SCANNER_SA_NAME)@$(PROJECT_ID).iam.gserviceaccount.com
# The backend worker SA that is allowed to invoke this scanner (must match the
# backend Makefile's WORKER_SA_NAME).
WORKER_SA_NAME   ?= praxedo-worker
WORKER_SA        := $(WORKER_SA_NAME)@$(PROJECT_ID).iam.gserviceaccount.com

# ---- GCS bucket the scanner reads from (same default as the backend Makefile) ----
BUCKET           ?= $(PROJECT_ID)-praxedo-files

# ---- Sidecar image ----
CLAMAV_IMAGE     ?= clamav/clamav-debian:latest

# ---- Derived ----
IMAGE_URI        := $(REGION)-docker.pkg.dev/$(PROJECT_ID)/$(REPO)/$(IMAGE):$(IMAGE_TAG)

DEPLOY_DIR := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))
REPO_ROOT  := $(abspath $(DEPLOY_DIR)/..)

.DEFAULT_GOAL := help

.PHONY: help config scanner-sa build-push deploy iam url all

help: ## Show this help.
	@echo "praxedo-upload-scanner - GCP deployment targets:"
	@echo ""
	@echo "  scanner-sa    Create the scanner runtime service account"
	@echo "  build-push    Build the image with Cloud Build and push to Artifact Registry"
	@echo "  deploy        Deploy the private scanner Cloud Run service (app + ClamAV sidecar)"
	@echo "  iam           Grant the scanner read on the bucket + the worker run.invoker on the scanner"
	@echo "  url           Print the deployed scanner URL"
	@echo "  all           scanner-sa + build-push + deploy + iam"
	@echo ""
	@echo "Override variables, e.g.: make deploy PROJECT_ID=my-proj REGION=europe-west1"

config: ## Print the resolved configuration.
	@echo "PROJECT_ID       = $(PROJECT_ID)"
	@echo "REGION           = $(REGION)"
	@echo "IMAGE_URI        = $(IMAGE_URI)"
	@echo "SCANNER_SERVICE  = $(SCANNER_SERVICE)  (SA: $(SCANNER_SA))"
	@echo "WORKER_SA        = $(WORKER_SA)  (granted run.invoker)"
	@echo "BUCKET           = $(BUCKET)  (scanner gets objectViewer)"

scanner-sa: ## Create the scanner runtime service account (idempotent).
	@gcloud iam service-accounts describe $(SCANNER_SA) --project=$(PROJECT_ID) >/dev/null 2>&1 \
	  || gcloud iam service-accounts create $(SCANNER_SA_NAME) \
	       --display-name="praxedo scanner runtime" --project=$(PROJECT_ID)

build-push: ## Build the image with Docker and push to Artifact Registry.
	docker build -t $(IMAGE_URI) $(REPO_ROOT)
	docker push $(IMAGE_URI)
	# Docker must be authenticated to $(REGION)-docker.pkg.dev first.
	# CI logs in via docker/login-action (WIF access token); locally, run once:
	#   gcloud auth configure-docker $(REGION)-docker.pkg.dev

# Two containers (app + ClamAV sidecar), so deploy imperatively with the
# multi-container `gcloud run deploy --container ...` syntax. Service-level flags
# come BEFORE the first --container; container-scoped flags follow their --container.
#   - app depends on clamav (--depends-on): it starts only once clamd's TCP
#     startup probe passes, so no scan hits a cold clamd.
#   - CPU is always allocated (--no-cpu-throttling) and one instance stays warm
#     (--min-instances=1) to keep the ClamAV signature DB loaded.
deploy: ## Deploy the private scanner Cloud Run service (app + ClamAV sidecar).
	gcloud run deploy $(SCANNER_SERVICE) \
	  --region=$(REGION) --project=$(PROJECT_ID) \
	  --service-account=$(SCANNER_SA) \
	  --no-allow-unauthenticated --ingress=internal \
	  --min-instances=1 --max-instances=5 --no-cpu-throttling --timeout=550 \
	  --labels=app=praxedo-upload,component=scanner \
	  --container=app --image=$(IMAGE_URI) --port=8080 --cpu=1 --memory=512Mi \
	    --depends-on=clamav \
	    --set-env-vars=CLAMAV_HOST=127.0.0.1,CLAMAV_PORT=3310 \
	    --startup-probe=httpGet.path=/health,httpGet.port=8080,periodSeconds=15,failureThreshold=40 \
	    --liveness-probe=httpGet.path=/health,httpGet.port=8080,periodSeconds=30 \
	  --container=clamav --image=$(CLAMAV_IMAGE) --cpu=2 --memory=2Gi \
	    --startup-probe=tcpSocket.port=3310,periodSeconds=15,failureThreshold=40

iam: ## Bucket read-only for the scanner + run.invoker for the worker on this scanner.
	# Scanner reads objects to scan (read-only; the worker keeps delete rights).
	gcloud storage buckets add-iam-policy-binding gs://$(BUCKET) \
	  --member="serviceAccount:$(SCANNER_SA)" \
	  --role=roles/storage.objectViewer --project=$(PROJECT_ID)
	# Only the worker SA may invoke this (private) scanner service.
	gcloud run services add-iam-policy-binding $(SCANNER_SERVICE) \
	  --region=$(REGION) \
	  --member="serviceAccount:$(WORKER_SA)" \
	  --role=roles/run.invoker --project=$(PROJECT_ID)

url: ## Print the deployed scanner URL (use as SCANNER_URL for the worker).
	@gcloud run services describe $(SCANNER_SERVICE) \
	  --region=$(REGION) --project=$(PROJECT_ID) --format='value(status.url)'

all: ## scanner-sa + build-push + deploy + iam.
	$(MAKE) scanner-sa
	$(MAKE) build-push
	$(MAKE) deploy
	$(MAKE) iam
