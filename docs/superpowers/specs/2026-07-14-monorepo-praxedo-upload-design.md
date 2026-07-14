# Design — Migration vers un monorepo `praxedo-upload`

**Date** : 2026-07-14
**Statut** : proposé (en attente de validation utilisateur)

## 1. Contexte et décision

Le projet a démarré en **polyrepo** : trois dépôts Git indépendants, chacun avec son
remote GitHub, développés en parallèle via worktrees. La décision est prise de **passer
en monorepo** : un dépôt unique `praxedo-upload` regroupant tous les composants, la
documentation et la mémoire projet.

Motivation : le livrable attendu est « **un** dépôt Git public + README » (un seul point
d'entrée à cloner et à évaluer). Le monorepo simplifie la vue d'ensemble sans empêcher
le déploiement séparé de chaque service sur GCP.

## 2. État de départ (constaté le 2026-07-14)

| Composant | Remote GitHub | Branche courante | Historique | CI | Particularités |
|---|---|---|---|---|---|
| `praxedo-upload-backend` | `soulemantraore/praxedo-upload-backend` | `task/cicd-wif-setup` | 31 commits | `deploy.yml` (push main) | 2 fichiers `deploy/*` non commités ; worktree actif `task/remote-scanner-client` |
| `praxedo-upload-ui` | `soulemantraore/praxedo-upload-ui` | `task/frontend-deploy` | 19 commits | `deploy.yml` (push main) | divergence avec `develop` (ahead 1 / behind 1) |
| `praxedo-upload-scanner` | `soulemantraore/praxedo-upload-scanner` | `main` | 1 commit | aucune | propre |

La racine `praxedo-app/` n'est **pas** versionnée. Elle contient aussi `CLAUDE.md`,
`.claude/` (mémoire + sessions), `docs/`, ainsi que des dossiers de travail à ne pas
versionner (`.superpowers/`, `.worktrees/`, `claude_chats/`, dumps de session, `.DS_Store`).

## 3. Décisions actées

1. **Structure** : monorepo unique `praxedo-upload`.
2. **Historique** : **migré et préservé** via `git subtree` (chaque composant garde son
   historique complet, consultable via `git log -- <sous-dossier>/`).
3. **Emplacement** : **sur place** — le dossier `praxedo-app` devient le monorepo. Le nom
   de dossier local reste `praxedo-app` (chemins déjà référencés) ; le remote s'appelle
   `praxedo-upload`.
4. **Consolidation d'abord** : avant l'import, chaque composant est consolidé pour que
   rien ne soit laissé derrière (commit des fichiers pendants, réconciliation des branches
   divergentes, intégration du worktree).
5. **Anciens dépôts** : conservés sur GitHub et **archivés** (lecture seule) après
   validation — sauvegarde intégrale de l'historique d'origine.
6. **Déploiement séparé** conservé via **CI path-filtered** (un service ne se redéploie que
   si son sous-dossier change). Objectif : simple et straightforward.

## 4. Structure cible

```
praxedo-upload/                    (dossier local: praxedo-app ; remote: praxedo-upload)
├── .github/workflows/
│   ├── backend-deploy.yml         # paths: praxedo-upload-backend/**
│   ├── ui-deploy.yml              # paths: praxedo-upload-ui/**
│   └── scanner-deploy.yml         # paths: praxedo-upload-scanner/**
├── praxedo-upload-backend/        # subtree, historique préservé
├── praxedo-upload-scanner/        # subtree, historique préservé
├── praxedo-upload-ui/             # subtree, historique préservé
├── docs/                          # documentation projet + specs
├── .claude/                       # mémoire + sessions (versionnées)
├── CLAUDE.md                      # mis à jour : monorepo
├── README.md                      # NOUVEAU : porte d'entrée du livrable
└── .gitignore                     # racine : .DS_Store, .superpowers/, .worktrees/, dumps, claude_chats/
```

## 5. Plan de migration (phases)

### Phase 0 — Consolidation par composant (rien laissé derrière)
- **backend** : committer les 2 fichiers `deploy/Makefile` et `deploy/README.md` ;
  intégrer/vérifier la branche `task/remote-scanner-client` (worktree) ; nettoyer le
  worktree (`git worktree remove`) ; consolider sur une branche unique de référence ;
  pousser vers le remote (backup archive).
- **ui** : réconcilier `develop` et `task/frontend-deploy` (merge, résoudre la divergence
  1/1) sur une branche unique de référence ; pousser.
- **scanner** : `main` déjà propre — rien à faire.

### Phase 1 — Initialiser le monorepo sur place
- Sortir temporairement les 3 sous-dossiers du chemin (renommés/déplacés) pour qu'ils
  servent de **sources** subtree sans être vus comme sous-modules.
- `git init` à la racine `praxedo-app`.
- Premier commit : `README.md`, `CLAUDE.md` (mis à jour), `docs/`, `.claude/`, `.gitignore`.

### Phase 2 — Import subtree des 3 composants
- `git subtree add --prefix=praxedo-upload-backend <source> <branche-consolidée>`
- idem pour `-ui` et `-scanner`.
- Vérifier que `git log -- praxedo-upload-<x>/` montre bien l'historique d'origine.

### Phase 3 — CI path-filtered
- Déplacer/adapter les 2 `deploy.yml` existants vers `.github/workflows/` avec un filtre
  `on: push: branches: [main], paths: [praxedo-upload-<x>/**]`.
- Créer `scanner-deploy.yml` sur le même modèle.

### Phase 4 — Documentation et règles
- `README.md` racine : présentation, architecture (backend / scanner / ui), démarrage,
  déploiement, choix techniques, hypothèses, pistes d'amélioration.
- Mettre à jour `CLAUDE.md` : sections « Structure du dépôt » (polyrepo → monorepo),
  « Git / workflow », « Pièges connus ».
- `.gitignore` racine.

### Phase 5 — Publication et archivage
- Créer le dépôt GitHub `praxedo-upload`, ajouter le remote, `git push`.
- Vérifier les GitHub Actions (secrets/variables WIF à re-déclarer côté nouveau dépôt).
- Archiver les 3 anciens dépôts (lecture seule).

## 6. Points de vigilance
- **Worktrees** : nettoyer `.worktrees/task-remote-scanner` avant de manipuler le `.git`
  du backend, sinon références cassées.
- **Sous-modules involontaires** : ne jamais `git add` un dossier contenant encore un `.git`
  interne → d'où le déplacement des sources en Phase 1.
- **Secrets CI** : les secrets/variables WIF (GCP_WIF_PROVIDER, GCP_DEPLOY_SA, etc.) sont
  attachés aux anciens dépôts ; à re-configurer sur `praxedo-upload`.
- **Ne rien détruire** avant que le push du monorepo soit vérifié ; les anciens dépôts
  restent la sauvegarde.

## 7. Hors scope (YAGNI)
- Pas de refonte de la CI au-delà du path-filtering (on réutilise l'existant).
- Pas de renommage du dossier local ni des sous-dossiers composants.
- Pas de changement de code applicatif : uniquement réorganisation dépôt + CI + docs.
