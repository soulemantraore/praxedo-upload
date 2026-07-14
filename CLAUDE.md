# CLAUDE.md — praxedo-app

Règles actives à suivre chaque session. Garder < 200 lignes.

## Contexte projet

Projet confié : concevoir et développer un **micro-service de gestion de fichiers sécurisés** :

- Recevoir et conserver des fichiers (utilisateurs + systèmes tiers).
- Garantir qu'**aucun fichier n'est servi sans avoir été scanné par un antivirus**.
- Servir les fichiers validés via une API programmatique.
- Supporter de nombreux utilisateurs simultanés, fichiers de tailles très variables.

**Stack imposée** : Java / Spring Boot / React. **Déploiement** : GCP.
**Livrables** : dépôt Git public fonctionnel + README (choix techniques, hypothèses, pistes d'amélioration).

## Structure du dépôt

**Monorepo** : un seul dépôt Git `praxedo-upload` à la racine, regroupant tous les composants, chacun **déployé séparément** sur GCP :
- `praxedo-upload-backend/` — Java / Spring Boot.
- `praxedo-upload-ui/` — React.
- `praxedo-upload-scanner/` — service antivirus externe (FastAPI + ClamAV).

Un `.gitignore` racine + un `.gitignore` par composant. `.superpowers/`, `.worktrees/`, `claude_chats/` sont des dossiers de travail temporaires ignorés. **Déploiement séparé** via GitHub Actions filtré par `paths:` — un workflow par composant, déclenché seulement si son sous-dossier change. Historique de chaque composant préservé (importé via `git subtree` ; consultable avec `git log -- praxedo-upload-<x>/`).

## Règles de travail

- Respecter **SOLID**, **sans sur-ingénierie** (YAGNI). Simplicité d'abord.
- Rester strictement sur la stack imposée.
- Communiquer en **français** (accents corrects), identifiants de code en anglais.
- Suivre le workflow superpowers : brainstorming → spec → plan → implémentation.
- Ne pas coder tant que la spec n'est pas validée par l'utilisateur.
- **Organisation des packages par rôle** : dans un package d'infrastructure qui accumule plusieurs rôles, séparer en **sous-packages** (`entities` / `repositories` / `adapters`) plutôt que tous les fichiers à plat. Ex. `infrastructure/persistence/jpa/{entities,repositories,adapters}`.

## Mémoire et contexte — trois tiroirs, une seule place par info

- **CLAUDE.md** (ce fichier) : règles actives, chaque session.
- **`.claude/memory/`** : connaissances durables, vérifiables, réutilisables. Chargées à la demande.
  Un fichier par sujet + `index.md` (une ligne par fichier sujet).
- **`.claude/sessions/`** : journal daté — « voilà ce qui s'est passé » (une entrée par session).

Règles :
- La mémoire est **versionnée et partagée via Git**, pas locale à la machine.
- Auto-memory désactivée intentionnellement (`.claude/settings` → `enabled: false`).
- **Début de session** : lire `.claude/memory/index.md` et le dernier récap dans `.claude/sessions/`.
- **Fin de session** : écrire le récap du jour + capturer les décisions durables dans `.claude/memory/`.

## Commandes

**Backend** (`praxedo-upload-backend/`, Java 21 via Homebrew, Maven) — Java 21 n'est **pas** le JDK par défaut (la machine a Java 17), donc **toujours préfixer `mvn` par ce `JAVA_HOME`** :

- Build + tests : `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn -f praxedo-upload-backend/pom.xml test`
- Lancer en local : `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn -f praxedo-upload-backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=local`

**Git / workflow** : repo unique `git@github.com:soulemantraore/praxedo-upload.git` (monorepo ; anciens repos `-backend`/`-ui`/`-scanner` archivés en lecture seule). Branche défaut `main`, intégration `develop`. **Une branche + une PR par tâche** (vers `develop`), revue et fusion par le mainteneur. **Pas de worktrees** : créer une **branche classique** (`git checkout -b task/…` depuis `develop`) puis ouvrir la PR depuis cette branche. Tâches dépendantes bloquées jusqu'à la fusion de leur prérequis.

## Pièges connus

- **Java 21 n'est pas le JDK par défaut** (machine en Java 17) → préfixer chaque `mvn` par le `JAVA_HOME` openjdk@21 (voir Commandes).
- **Validateur de commandes** : bloque les caractères accentués, la séquence `===`, et les chemins sous `/usr` ou contenant `/bin/`. Garder les commandes shell et messages de commit en **ASCII** ; pour Java 21 utiliser `JAVA_HOME=… mvn` (jamais `$JAVA_HOME/bin/java`).
- **Adapters `@Profile({"local","test"})`** : tout `@SpringBootTest` doit porter `@ActiveProfiles("test")`, sinon aucun bean repo/storage → contexte non câblé.
- **MockMvc double-encode les `%2F`** d'une URL passée comme *template* (`%2F`→`%252F`). Pour les clés de stockage avec `/`, passer la valeur **décodée** via `.param("key", …)` (un vrai client HTTP n'a pas ce souci).
- **Pas de worktrees** (décision 2026-07-14) : travailler sur des **branches classiques** (`git checkout -b`) et ouvrir les PR depuis ces branches ; après fusion, `git branch -D` pour nettoyer.
- **Monorepo + sous-dossiers** : les composants ont été importés via `git subtree` (2026-07-14). Ne jamais `git add` un dossier contenant encore un `.git` interne (git le prendrait pour un sous-module). L'historique d'un composant se consulte avec `git log -- praxedo-upload-<x>/`.
