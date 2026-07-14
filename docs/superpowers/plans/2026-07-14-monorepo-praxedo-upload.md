# Monorepo `praxedo-upload` — Plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fusionner les trois dépôts (`praxedo-upload-backend`, `praxedo-upload-ui`, `praxedo-upload-scanner`) en un seul monorepo Git `praxedo-upload`, sur place dans le dossier `praxedo-app`, en préservant l'historique complet de chaque composant.

**Architecture:** La branche d'intégration distante `origin/develop` de chaque composant est déjà à jour (tout le travail y est mergé via PR par le mainteneur). On importe donc directement `origin/develop` (backend, ui) et `origin/main` (scanner) via `git subtree` sous un sous-dossier dédié, dans un dépôt neuf initialisé sur place. Déploiement séparé conservé via GitHub Actions filtré par `paths:`. Anciens dépôts archivés en lecture seule (sauvegarde).

**Tech Stack:** Git (subtree), GitHub CLI (`gh`), GitHub Actions, GCP Cloud Run (déjà en place). Backend Java 21 / Maven, UI React / Vite, Scanner FastAPI.

**Convention commandes :** toutes les commandes sont **ASCII** (le validateur local bloque accents, `===`, `/usr`, `/bin/`). Les chemins sont absolus depuis `/Users/soulemantraore/Documents/praxedo-app` (abrégé `ROOT` ci-dessous). Le JDK 21 n'est pas par défaut : préfixer chaque `mvn` par `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`.

---

## Contexte révisé (2026-07-14, en cours d'exécution)

Diagnostic corrigé après `git fetch` frais des 3 dépôts :

- `origin/develop` du **backend** contient déjà, via PR mergées : `#29` (CI/WIF) et `#30` (externalisation du scan AV, ADR D15). C'est la version canonique.
- `origin/develop` du **ui** contient déjà les PR `#5 → #19`, dont `#18` (contrat backend) et `#19` (déploiement Cloud Run).
- `origin/develop` du **scanner** == `origin/main` (tout est là).

**Conséquence :** aucune consolidation manuelle ni merge de branches `task/*` locales (ce sont des reliquats pré-PR). On importe `origin/develop` (backend, ui) et `origin/main` (scanner). Le delta local `PROJECT_ID = praxedo-upload-test` est volontairement abandonné (le dépôt public garde le placeholder `my-gcp-project`, surchargeable).

---

## Vue d'ensemble des fichiers

**Créés :**
- `ROOT/.git/` — nouveau dépôt monorepo (via `git init`)
- `ROOT/.gitignore` — ignore racine (OS, dossiers de travail, dumps)
- `ROOT/README.md` — porte d'entrée du livrable
- `ROOT/.github/workflows/backend-deploy.yml` — CI backend (paths-filtered)
- `ROOT/.github/workflows/ui-deploy.yml` — CI UI (paths-filtered)
- `ROOT/.github/workflows/scanner-deploy.yml` — CI scanner (paths-filtered, nouveau)

**Modifiés :**
- `ROOT/CLAUDE.md` — sections « Structure du dépôt », « Git / workflow », « Pièges connus »

**Importés tels quels (subtree depuis les remotes, historique préservé) :**
- `ROOT/praxedo-upload-backend/` (origin/develop), `ROOT/praxedo-upload-ui/` (origin/develop), `ROOT/praxedo-upload-scanner/` (origin/main)

---

## Task 1 : Vérifier la source canonique et nettoyer les reliquats

**Objectif :** confirmer que `origin/develop` (backend, ui) et `origin/main` (scanner) sont à jour, et remettre au propre l'état local touché pendant le diagnostic.

- [ ] **Step 1 : Confirmer les branches distantes à jour (déjà fetchées)**

```bash
cd /Users/soulemantraore/Documents/praxedo-app
git -C praxedo-upload-backend log --oneline -3 origin/develop
git -C praxedo-upload-ui log --oneline -3 origin/develop
git -C praxedo-upload-scanner log --oneline -3 origin/main
```
Attendu : backend `531bc3e … (#30)` en tête ; ui `9ea150f … (#19)` en tête ; scanner `693f98b`.

- [ ] **Step 2 : Supprimer la branche `task/remote-scanner-client` poussée par erreur pendant le diagnostic**

```bash
git -C praxedo-upload-backend push origin --delete task/remote-scanner-client
```
Attendu : la branche disparait du remote (son contenu est déjà dans `origin/develop` via la PR #30 ; l'archive reste propre).

(Aucune autre action : les dossiers locaux seront déplacés en Task 4 puis supprimés en Task 9 ; l'état local bricolé de `develop` backend part avec eux, sans effet puisqu'on importe la version distante.)

---

## Task 4 : Initialiser le monorepo sur place

**Objectif :** le dossier `praxedo-app` devient un dépôt git neuf, avec un premier commit contenant docs/mémoire/README/CLAUDE.md, SANS les composants (importés à la Task 5).

- [ ] **Step 1 : Déplacer les 3 composants hors du chemin (pour libérer les préfixes)**

```bash
mkdir -p /Users/soulemantraore/Documents/praxedo-migration-src
cd /Users/soulemantraore/Documents/praxedo-app
mv praxedo-upload-backend praxedo-upload-ui praxedo-upload-scanner /Users/soulemantraore/Documents/praxedo-migration-src/
ls -1
```
Attendu : `praxedo-app` ne contient plus les 3 sous-dossiers. (Ils sont conservés en secours dans `praxedo-migration-src/` jusqu'à validation finale ; l'import se fait depuis les remotes GitHub, pas depuis ces copies.)

- [ ] **Step 2 : Initialiser le dépôt monorepo sur la branche main**

```bash
cd /Users/soulemantraore/Documents/praxedo-app
git init -b main
```
Attendu : `Initialized empty Git repository in .../praxedo-app/.git/`.

- [ ] **Step 3 : Créer le `.gitignore` racine**

Créer `ROOT/.gitignore` avec ce contenu exact :

```gitignore
# OS
.DS_Store

# Dossiers de travail temporaires (non versionnes)
.superpowers/
.worktrees/
claude_chats/

# Dumps de session
*-this-session-*.txt

# Reglages locaux Claude Code (specifiques a la machine)
.claude/settings.local.json
```

- [ ] **Step 4 : Supprimer l'artefact `package-lock.json` racine (vide, sans package.json)**

```bash
cd /Users/soulemantraore/Documents/praxedo-app
cat package-lock.json
rm package-lock.json
```

- [ ] **Step 5 : Mettre à jour `CLAUDE.md` — section « Structure du dépôt »**

Remplacer le bloc actuel :

```markdown
## Structure du dépôt

**Polyrepo** : deux dépôts Git séparés dans ce dossier, déployés séparément sur GCP :
- `praxedo-upload-backend` — Java / Spring Boot (son propre `.gitignore`).
- `praxedo-upload-ui` — React (son propre `.gitignore`).

Un `.gitignore` par dépôt. `.superpowers/` est un dossier de travail temporaire à ignorer.
```

par :

```markdown
## Structure du dépôt

**Monorepo** : un seul dépôt Git `praxedo-upload` à la racine, regroupant tous les composants, chacun **déployé séparément** sur GCP :
- `praxedo-upload-backend/` — Java / Spring Boot.
- `praxedo-upload-ui/` — React.
- `praxedo-upload-scanner/` — service antivirus externe (FastAPI + ClamAV).

Un `.gitignore` racine + un `.gitignore` par composant. `.superpowers/`, `.worktrees/`, `claude_chats/` sont des dossiers de travail temporaires ignorés. **Déploiement séparé** via GitHub Actions filtré par `paths:` — un workflow par composant, qui ne se déclenche que si son sous-dossier change.
```

- [ ] **Step 6 : Mettre à jour `CLAUDE.md` — ligne « Git / workflow »**

Remplacer :

```markdown
**Git / workflow** : repo `git@github.com:soulemantraore/praxedo-upload-backend.git`. Branche défaut `main`, intégration `develop`.
```

par :

```markdown
**Git / workflow** : repo unique `git@github.com:soulemantraore/praxedo-upload.git`. Branche défaut `main`, intégration `develop`.
```
(Conserver la fin de la phrase inchangée.)

- [ ] **Step 7 : Ajouter le piège subtree dans « Pièges connus »**

Ajouter cette puce à la fin de la section « ## Pièges connus » :

```markdown
- **Monorepo + sous-dossiers avec `.git`** : ne jamais `git add` un dossier contenant encore un `.git` interne (git le prendrait pour un sous-module). Les composants ont été importés via `git subtree` ; l'historique de chacun se consulte avec `git log -- praxedo-upload-<x>/`.
```

- [ ] **Step 8 : Créer un README racine minimal (enrichi en Task 7)**

Créer `ROOT/README.md` avec :

```markdown
# praxedo-upload

Micro-service de gestion de fichiers sécurisés : aucun fichier n'est servi sans avoir été scanné par un antivirus.

Monorepo regroupant :
- `praxedo-upload-backend/` — API Java / Spring Boot.
- `praxedo-upload-scanner/` — service antivirus externe (FastAPI + ClamAV).
- `praxedo-upload-ui/` — portail React.

> Documentation détaillée en cours de rédaction (voir `docs/`).
```

- [ ] **Step 9 : Premier commit**

```bash
cd /Users/soulemantraore/Documents/praxedo-app
git add .gitignore README.md CLAUDE.md docs .claude
git status --short
git commit -m "chore: bootstrap praxedo-upload monorepo (docs, memory, README)"
git status --ignored --short | head -20
```
Attendu : commit créé ; `.superpowers/`, `.worktrees/`, `claude_chats/`, dumps `.txt`, `.DS_Store` NON suivis (ignorés).

---

## Task 5 : Importer les 3 composants via subtree (depuis les remotes, historique préservé)

**Objectif :** chaque composant sous son sous-dossier, avec tout son historique, depuis la version canonique distante.

**Répertoire :** `ROOT` (`praxedo-app`)

- [ ] **Step 1 : Importer le backend (origin/develop)**

```bash
cd /Users/soulemantraore/Documents/praxedo-app
git subtree add --prefix=praxedo-upload-backend git@github.com:soulemantraore/praxedo-upload-backend.git develop
```
Attendu : `Added dir 'praxedo-upload-backend'`.

- [ ] **Step 2 : Importer le UI (origin/develop)**

```bash
git subtree add --prefix=praxedo-upload-ui git@github.com:soulemantraore/praxedo-upload-ui.git develop
```
Attendu : `Added dir 'praxedo-upload-ui'`.

- [ ] **Step 3 : Importer le scanner (origin/main)**

```bash
git subtree add --prefix=praxedo-upload-scanner git@github.com:soulemantraore/praxedo-upload-scanner.git main
```
Attendu : `Added dir 'praxedo-upload-scanner'`.

- [ ] **Step 4 : Vérifier l'historique de chaque composant**

```bash
echo "backend:"; git log --oneline -- praxedo-upload-backend/ | wc -l
echo "ui:";      git log --oneline -- praxedo-upload-ui/ | wc -l
echo "scanner:"; git log --oneline -- praxedo-upload-scanner/ | wc -l
git log --oneline | grep -E "531bc3e|9ea150f|1070cfb|693f98b" | head
```
Attendu : backend et ui avec un historique riche (dizaines de commits), scanner >= 1 ; commits repères retrouvés.

- [ ] **Step 5 : Vérifier que ce sont bien du contenu normal (pas des sous-modules)**

```bash
git ls-files praxedo-upload-backend | head -3
ls -la praxedo-upload-backend/.git 2>/dev/null || echo "OK: pas de .git interne"
```
Attendu : fichiers listés, pas de `.git` interne.

- [ ] **Step 6 : Smoke build des composants (détecter tôt un souci)**

```bash
cd /Users/soulemantraore/Documents/praxedo-app/praxedo-upload-backend
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn -q -DskipTests package
cd /Users/soulemantraore/Documents/praxedo-app/praxedo-upload-ui
npm ci && npm run build
```
Attendu : `BUILD SUCCESS` backend ; build Vite OK. (Si `npm ci` échoue faute de lockfile, `npm install`.)

---

## Task 6 : CI path-filtered (un workflow par composant, à la racine)

**Objectif :** GitHub Actions ne lit que `.github/workflows/` à la racine. On remonte les workflows importés et on ajoute un filtre `paths:` pour que chaque service ne se redéploie que si son sous-dossier change.

- [ ] **Step 1 : Déplacer et renommer le workflow backend**

```bash
cd /Users/soulemantraore/Documents/praxedo-app
mkdir -p .github/workflows
git mv praxedo-upload-backend/.github/workflows/deploy.yml .github/workflows/backend-deploy.yml
```

- [ ] **Step 2 : Ajouter le filtre `paths` au workflow backend**

Dans `.github/workflows/backend-deploy.yml`, remplacer le bloc `on:` :

```yaml
on:
  push:
    branches: [ main ]
  workflow_dispatch: {}
```

par :

```yaml
on:
  push:
    branches: [ main ]
    paths:
      - 'praxedo-upload-backend/**'
      - '.github/workflows/backend-deploy.yml'
  workflow_dispatch: {}
```

Puis ajouter au job un `defaults.run.working-directory` (au même niveau que `runs-on`) :

```yaml
    defaults:
      run:
        working-directory: praxedo-upload-backend
```
Vérifier les étapes `docker build`/`gcloud` qui référencent des fichiers : les préfixer par `praxedo-upload-backend/` si elles n'utilisent pas `working-directory`.

- [ ] **Step 3 : Déplacer et adapter le workflow UI (même principe)**

```bash
git mv praxedo-upload-ui/.github/workflows/deploy.yml .github/workflows/ui-deploy.yml
```
Dans `.github/workflows/ui-deploy.yml`, remplacer :

```yaml
on:
  push:
    branches: [main]
  workflow_dispatch:
```
par :

```yaml
on:
  push:
    branches: [main]
    paths:
      - 'praxedo-upload-ui/**'
      - '.github/workflows/ui-deploy.yml'
  workflow_dispatch:
```
Et ajouter `defaults.run.working-directory: praxedo-upload-ui` au job de build (mêmes précautions).

- [ ] **Step 4 : Créer le workflow scanner**

Créer `.github/workflows/scanner-deploy.yml` sur le même modèle. Contenu de départ (à ajuster selon le `Dockerfile` du scanner) :

```yaml
# Deploie praxedo-upload-scanner sur Cloud Run.
name: Deploy Scanner to Cloud Run

on:
  push:
    branches: [main]
    paths:
      - 'praxedo-upload-scanner/**'
      - '.github/workflows/scanner-deploy.yml'
  workflow_dispatch:

concurrency:
  group: deploy-scanner-${{ github.ref }}
  cancel-in-progress: true

jobs:
  deploy:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: praxedo-upload-scanner
    permissions:
      contents: read
      id-token: write
    steps:
      - uses: actions/checkout@v4
      - id: auth
        uses: google-github-actions/auth@v2
        with:
          workload_identity_provider: ${{ secrets.GCP_WIF_PROVIDER }}
          service_account: ${{ secrets.GCP_DEPLOY_SA }}
      - uses: google-github-actions/setup-gcloud@v2
      - name: Build and deploy
        run: |
          gcloud builds submit --tag "${{ vars.GCP_REGION }}-docker.pkg.dev/${{ vars.GCP_PROJECT_ID }}/${{ vars.ARTIFACT_REPO }}/praxedo-upload-scanner:${{ github.sha }}"
          gcloud run deploy praxedo-upload-scanner \
            --image "${{ vars.GCP_REGION }}-docker.pkg.dev/${{ vars.GCP_PROJECT_ID }}/${{ vars.ARTIFACT_REPO }}/praxedo-upload-scanner:${{ github.sha }}" \
            --region "${{ vars.GCP_REGION }}" --no-allow-unauthenticated
```
(Si l'infra GCP du scanner n'est pas encore provisionnée, ne garder que `workflow_dispatch:` et retirer le bloc `push:`.)

- [ ] **Step 5 : Commit de la CI**

```bash
cd /Users/soulemantraore/Documents/praxedo-app
git add .github/
git commit -m "ci: path-filtered deploy workflows per component (backend, ui, scanner)"
```

---

## Task 7 : README de livrable (porte d'entrée)

- [ ] **Step 1 : Rédiger le README complet**

Réécrire `ROOT/README.md` en couvrant, dans cet ordre : (1) objectif du micro-service et garantie « pas de fichier servi sans scan AV » ; (2) schéma d'architecture des 3 composants et du flux fichier (upload -> quarantaine -> scan -> disponible) ; (3) démarrage local (backend avec `JAVA_HOME` JDK21, UI `npm`, scanner FastAPI) ; (4) déploiement GCP Cloud Run + CI path-filtered ; (5) choix techniques et hypothèses ; (6) pistes d'amélioration. S'appuyer sur les READMEs/ADR de chaque sous-dossier et sur `docs/` (pointer vers eux plutôt que dupliquer).

- [ ] **Step 2 : Commit**

```bash
cd /Users/soulemantraore/Documents/praxedo-app
git add README.md
git commit -m "docs: monorepo README (architecture, run, deploy, choices)"
```

---

## Task 8 : Publier sur GitHub

- [ ] **Step 1 : Vérifier si le dépôt distant existe déjà**

```bash
gh repo view soulemantraore/praxedo-upload >/dev/null 2>&1 && echo "EXISTE" || echo "A CREER"
```

- [ ] **Step 2 : Créer le dépôt s'il n'existe pas (public)**

```bash
# Uniquement si Step 1 a affiche "A CREER" :
gh repo create soulemantraore/praxedo-upload --public --description "Micro-service de gestion de fichiers securises (backend + scanner AV + UI)"
```

- [ ] **Step 3 : Ajouter le remote et pousser**

```bash
cd /Users/soulemantraore/Documents/praxedo-app
git remote add origin git@github.com:soulemantraore/praxedo-upload.git
git remote -v
git push -u origin main
```

- [ ] **Step 4 : Configurer les secrets/variables Actions**

Re-déclarer les secrets/variables WIF sur le nouveau dépôt (lister d'abord ceux des anciens) :

```bash
gh variable list --repo soulemantraore/praxedo-upload-backend
gh variable list --repo soulemantraore/praxedo-upload-ui
gh secret set GCP_WIF_PROVIDER --repo soulemantraore/praxedo-upload
gh secret set GCP_DEPLOY_SA --repo soulemantraore/praxedo-upload
gh variable set GCP_PROJECT_ID --repo soulemantraore/praxedo-upload
gh variable set GCP_REGION --repo soulemantraore/praxedo-upload
gh variable set ARTIFACT_REPO --repo soulemantraore/praxedo-upload
# + variables specifiques (GCS_BUCKET, UI_ORIGIN, VITE_API_BASE_URL, VITE_API_KEY, VITE_PORTAL_NAME)
```

- [ ] **Step 5 : Vérifier l'exécution des workflows**

```bash
gh run list --repo soulemantraore/praxedo-upload --limit 5
```

---

## Task 9 : Archiver les anciens dépôts et nettoyer

- [ ] **Step 1 : Confirmer que le monorepo est complet et poussé**

```bash
gh repo view soulemantraore/praxedo-upload --json name,defaultBranchRef,pushedAt
git -C /Users/soulemantraore/Documents/praxedo-app log --oneline -5
```

- [ ] **Step 2 : Archiver les 3 anciens dépôts GitHub (lecture seule)**

```bash
gh repo archive soulemantraore/praxedo-upload-backend --yes
gh repo archive soulemantraore/praxedo-upload-ui --yes
gh repo archive soulemantraore/praxedo-upload-scanner --yes
```

- [ ] **Step 3 : Retirer les sources temporaires locales (après vérification)**

```bash
ls -1 /Users/soulemantraore/Documents/praxedo-migration-src
rm -rf /Users/soulemantraore/Documents/praxedo-migration-src
```

- [ ] **Step 4 : Vérification finale**

```bash
cd /Users/soulemantraore/Documents/praxedo-app
git status -sb
ls -1
git log --oneline | wc -l
```
Attendu : working tree propre ; 3 composants + `docs/`, `.claude/`, `CLAUDE.md`, `README.md`, `.github/` présents.

---

## Notes de sécurité (irréversibilité)

- Ne PAS exécuter la Task 9 (archivage/suppression) tant que la Task 8 n'est pas vérifiée verte.
- Les anciens remotes contiennent tout le travail (PR mergées sur `origin/develop`) : ils restent le filet de sécurité jusqu'à validation complète.
- Smoke build backend + ui en Task 5 pour détecter tôt un souci d'import.
