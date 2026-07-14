# =====================================================================
# praxedo-upload-ui - image de production (SPA statique)
#
# Multi-stage : build Vite avec Node 20, puis service des fichiers statiques
# par `serve` (Node, sans nginx). Sur Cloud Run, on ecoute sur $PORT.
#
# IMPORTANT : Vite inline les variables VITE_* AU MOMENT DU BUILD -> elles
# sont passees en --build-arg (voir .github/workflows/deploy.yml), pas au runtime.
# =====================================================================

# ---- Stage 1 : build ----
FROM node:20-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

ARG VITE_API_BASE_URL
ARG VITE_API_KEY
ARG VITE_PORTAL_NAME=Praxedo
ARG VITE_USE_MOCK=false
ARG VITE_POLL_MS=2500
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL \
    VITE_API_KEY=$VITE_API_KEY \
    VITE_PORTAL_NAME=$VITE_PORTAL_NAME \
    VITE_USE_MOCK=$VITE_USE_MOCK \
    VITE_POLL_MS=$VITE_POLL_MS

COPY . .
RUN npm run build

# ---- Stage 2 : runtime ----
FROM node:20-alpine AS runtime
WORKDIR /app

# `serve` sert le build statique avec repli SPA (-s) sur index.html.
RUN npm install -g serve@14
COPY --from=build /app/dist ./dist

ENV PORT=8080
EXPOSE 8080
# Cloud Run fournit $PORT ; on ecoute sur toutes les interfaces.
CMD ["sh", "-c", "serve -s dist -l tcp://0.0.0.0:${PORT}"]
