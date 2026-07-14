# praxedo-upload

Micro-service de **gestion de fichiers sécurisés** : recevoir des fichiers (utilisateurs et
systèmes tiers), les faire scanner par un antivirus, et ne servir que ceux qui sont sains.

> **Invariant de sécurité** : un fichier n'est **jamais** servi tant qu'il n'a pas été validé
> (`CLEAN`) par l'antivirus. Tout ce qui suit découle de cette garantie.

Monorepo regroupant les trois composants du système, **déployés séparément** sur GCP :

| Composant | Rôle | Stack | Détails |
|---|---|---|---|
| [`praxedo-upload-backend/`](praxedo-upload-backend/README.md) | API : enregistrement des uploads, orchestration du scan, téléchargement contrôlé | Java 21 · Spring Boot 3.3 · Maven | archi hexagonale |
| [`praxedo-upload-scanner/`](praxedo-upload-scanner/README.md) | Service antivirus externe : scanne un objet GCS via ClamAV, rend un verdict | Python 3.12 · FastAPI · ClamAV | sans état, sans base |
| [`praxedo-upload-ui/`](praxedo-upload-ui/README.md) | Portail : dépôt de fichiers, suivi du scan en direct, téléchargement des fichiers sains | React 18 · Vite · TypeScript | démo hors-ligne (MSW) |

## Architecture d'ensemble

Le scan vit dans un **service séparé** (`praxedo-upload-scanner`) que le backend **appelle** ;
le scanner ne touche jamais la base — seul le backend écrit le verdict (ADR **D15**).

```
                 ┌─────────────────┐        X-API-Key
   navigateur ──▶│ praxedo-upload-ui│──────────────────────┐
                 └─────────────────┘                       ▼
                                              ┌───────────────────────────┐
   upload direct (URL signée)  ──────────────▶│      GCS (bucket)         │
                                              └───────────────────────────┘
                                                     │ object finalize
                                                     ▼
                                              Pub/Sub ──push──▶ /internal/scan-events
                                                                      │ (scan-worker)
                 ┌──────────────────────────┐   POST /scan {gsUri}    ▼
                 │ praxedo-upload-backend    │◀───────────────┐  ┌──────────────────┐
                 │ (domaine + ports/adapters)│  verdict        └─▶│ praxedo-upload-  │
                 │  écrit le verdict (Cloud  │◀──────────────────│ scanner + ClamAV │
                 │  SQL)                     │                    └──────────────────┘
                 └──────────────────────────┘
   GET /content ──▶ URL signée de download **uniquement si CLEAN**, sinon 403
```

**Cycle de vie d'un fichier** :

```
PENDING → SCANNING → CLEAN        (seul état téléchargeable)
                   → INFECTED     (octets supprimés, download → 403)
                   → SCAN_FAILED  (échec technique, retries bornés)
PENDING → EXPIRED  (upload jamais reçu, réconciliation par TTL)
```

## Démarrage rapide (local)

Chaque composant a un README détaillé ; voici l'essentiel.

**UI seule (démo hors-ligne, aucun backend requis)** — le plus rapide pour voir le produit :

```bash
cd praxedo-upload-ui
npm install
npm run dev            # http://localhost:5173  (faux backend MSW, VITE_USE_MOCK=true par defaut)
```

**Backend (profil `local`, sans dépendance GCP)** — JDK 21 requis :

```bash
cd praxedo-upload-backend
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  mvn spring-boot:run -Dspring-boot.run.profiles=local
# une cle API de demo est loguee au demarrage : pk_...
```

**UI contre le vrai backend** : renseigner `VITE_API_BASE_URL`, `VITE_API_KEY` (la clé loguée),
`VITE_USE_MOCK=false` dans `praxedo-upload-ui/.env` (voir son README).

**Scanner (optionnel en local)** : le backend en profil `local` utilise un faux antivirus
(détection EICAR) et n'a pas besoin du scanner. Pour expérimenter le flux réel complet
(scanner + ClamAV + émulateurs Pub/Sub/GCS + Postgres), voir `praxedo-upload-backend/docker-compose.yml`.

## Tests

```bash
# Backend (JDK 21)
cd praxedo-upload-backend && JAVA_HOME=<jdk-21> mvn test
# UI
cd praxedo-upload-ui && npm test
# Scanner
cd praxedo-upload-scanner && pip install -r requirements-dev.txt && pytest
```

## Déploiement (GCP Cloud Run)

Chaque composant se déploie **indépendamment** sur Cloud Run. La CI GitHub Actions est
**filtrée par chemin** (`.github/workflows/`) : un push ne redéploie que le composant dont
le sous-dossier a changé.

| Workflow | Déclencheur | Cible |
|---|---|---|
| `backend-deploy.yml` | push `main` touchant `praxedo-upload-backend/**` | Cloud Run (api + worker) |
| `ui-deploy.yml` | push `main` touchant `praxedo-upload-ui/**` | Cloud Run (SPA servie par `serve`) |
| `scanner-deploy.yml` | **manuel** (`workflow_dispatch`) | Cloud Run privé (app + sidecar ClamAV) |

L'authentification se fait par **Workload Identity Federation** (aucune clé JSON longue durée) ;
les secrets/variables requis (`GCP_WIF_PROVIDER`, `GCP_DEPLOY_SA`, `GCP_PROJECT_ID`, `GCP_REGION`,
`GCS_BUCKET`, `VITE_API_BASE_URL`, `VITE_API_KEY`, …) sont documentés en tête de chaque workflow.
Le déploiement du scanner reste manuel tant que son bootstrap GCP (SA + IAM) n'a pas été exécuté
une fois (`praxedo-upload-scanner/deploy/Makefile`).

## Choix techniques & hypothèses (transverses)

- **Flux asynchrone** : l'upload répond immédiatement (`PENDING`), le scan tourne en arrière-plan
  → tient la charge et les fichiers de tailles très variables.
- **URLs signées direct-to-GCS** : les octets ne transitent jamais par l'application (le port
  `FileStorage` cache ce détail ; l'adapter local est un proxy HTTP).
- **Antivirus derrière un port** : en production `RemoteScannerClient` appelle le service scanner
  externe (ClamAV) via OIDC ; un `FakeAntivirusScanner` (EICAR) sert en local/test. Un adapter SaaS
  serait trivial à brancher.
- **Sécurité machine-à-machine** : clés API par client (hachées SHA-256), scoping par owner (chaque
  client ne voit que ses fichiers). Hypothèse : appels de systèmes tiers ; l'authentification humaine
  (OAuth2/JWT) est une évolution documentée.

## Pistes d'amélioration

- Authentification humaine OAuth2/JWT en complément des clés API ; endpoint admin de gestion des clés.
- Webhooks / push de fin de scan pour supprimer le polling côté UI.
- Téléchargement direct depuis GCS via URL signée renvoyée au navigateur (décharge le backend des gros fichiers).
- Durcissement : vérification applicative du jeton OIDC Pub/Sub, chiffrement au repos (CMEK), quotas/rate-limiting.
- Dépendances UI : traiter les vulnérabilités `npm audit` (5 relevées au 2026-07-14) avant mise en production.

## Organisation du dépôt

Monorepo : un `.gitignore` racine + un `.gitignore` par composant. Chaque composant conserve son
historique complet (importé via `git subtree` ; `git log -- praxedo-upload-<x>/`). La documentation
de conception (specs, plans, ADR) est sous [`docs/`](docs/). Branche par défaut `main`, intégration
`develop` ; une branche + une PR par tâche.
