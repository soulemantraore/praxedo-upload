# Session 2026-07-11/12 — Implémentation frontend (jalon 4)

## Objet
Implémenter le **jalon 4 = le frontend** `praxedo-upload-ui` (backend construit en parallèle). Le dépôt cloné était **vide** ; aucun plan frontend n'existait (seule la spec §10). Puis, en cours de route, consigne utilisateur : **reproduire fidèlement la maquette Cloud Design** `Fichiers sécurisés.dc.html`.

## Méthodologie (exigence utilisateur)
- Workflow superpowers : la spec §10 étant validée, **écriture du plan** (`docs/superpowers/plans/2026-07-11-frontend-ui.md`) avant de coder.
- Exécution **subagent-driven** : un implémenteur par tâche + revue (conformité spec puis qualité) ; **une branche + une PR par tâche vers `develop`** ; l'agent **relit, valide, merge (squash)** puis passe à la suivante (merge délégué à l'agent pour cette tâche agentique).
- Socle Git bootstrapé : `main` (commit initial README+gitignore) puis `develop`.

## Réalisations — 16 PR fusionnées dans `develop`
**Fondation (T1–T6)** : #1 scaffold Vite/React/TS/Vitest/MSW · #2 types + config · #3 `lib/format` (TDD) · #4 `api/client` (fetch injectable, X-API-Key) · #5 faux backend MSW (store + machine à états) · #6 hooks react-query.
**Composants (T7–T11)** : #7 Modal + StatusBadge · #8 Header + StatCards · #9 SearchBar + Pagination + FileTable · #10 UploadModal · #11 FileReportModal.
**Assemblage (T12)** : #12 App + bootstrap `main` (mode démo MSW, **fail-open**).
**Fidélité design (TDA/TDB)** : #13 fondations (IBM Plex, palette, format accentué, config identité client, mock sha256 + seedDemo) · #14 composants du corps de page réécrits en **styles inline copiés de la maquette** · #15 modales upload + détail réécrites (dropzone+3 étapes ; carte verdict + métadonnées + SHA-256 + frise cycle de vie + états).
**Finalisation (T13)** : #16 `public/mockServiceWorker.js` + README complet.

Suite finale : **35 tests verts**, typecheck clean, build OK.

## Bugs réels attrapés par les revues
- **T1** : `npm run lint` (`tsc -b --noEmit`) cassé (TS6310) → restructuration propre (drop du projet composite, `tsc` simple + script `typecheck`).
- **T4** : `request<T>` du plan plantait sur le 202 `rescan` (corps vide → `res.json()` throw) → lecture `res.text()` + parse conditionnel.
- **T5** : course de capture de `fetch` (client créé au top-level du test avant `beforeAll`) → `server.listen()` au **scope module** de `test/setup.ts` ; off-by-one révélation de scan (`+1`→`+2`).
- **T12** : `main.tsx` gate le rendu derrière `.then()` sans `.catch()` → **page blanche** si le worker MSW échoue → bootstrap **fail-open** (`try/catch` + `.finally(render)`).

## Vérification visuelle (Chrome, MCP claude-in-chrome)
Démo hors ligne lancée (`npm run dev`, worker MSW + 8 fichiers seedés). Captures comparées à la maquette : **page principale, modale d'upload, modale de détail (validé et infecté)** — fidélité **excellente**, aucune erreur console. Invariant visuel confirmé (téléchargement absent pour INFECTED, chip « Bloqué »).

## Maquette
Importée via le **MCP `DesignSync`** (projet claude.ai `0ca3fcaf-...`, fichier `Fichiers sécurisés.dc.html`). Copie de référence : `docs/design/Fichiers-securises.dc.html` (avec note de mapping design→données).

## Décisions durables → capturées dans `.claude/memory/frontend-ui.md`.

## Prochaines étapes possibles
- Jalons backend restants (2 ClamAV/Pub-Sub, 3 GCS/Cloud SQL) — en parallèle.
- Jalon infra (gcloud + Makefile, CI/CD GitHub Actions par dépôt, déploiement UI sur GCP).
- Brancher le frontend sur le vrai backend local (`VITE_USE_MOCK=false`) une fois les endpoints prêts.
