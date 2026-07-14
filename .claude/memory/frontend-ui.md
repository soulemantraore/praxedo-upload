# Frontend `praxedo-upload-ui` — architecture & décisions durables (jalon 4)

Dépôt : `git@github.com:soulemantraore/praxedo-upload-ui.git`. SPA React « Fichiers sécurisés ».
Plan d'exécution : `docs/superpowers/plans/2026-07-11-frontend-ui.md`.

## Stack & structure
- **Vite 5 + React 18 + TypeScript strict** ; **TanStack Query v5** (polling léger conditionnel : ne poll que s'il reste un fichier `PENDING`/`SCANNING`) ; **Vitest + Testing Library + MSW** ; polices **IBM Plex Sans/Mono**. Pas de state manager lourd. Auth par en-tête `X-API-Key`.
- Couches découplées (même esprit que le backend) :
  - `src/lib/format.ts` — logique de présentation **pure**, testée sans React (`formatBytes` base 1000 virgule FR, `formatDateShort/Long` en **UTC** pour des tests déterministes, `statusPresentation`→{label, tone}, `badgeColors`, `fileExtMeta`, `isDownloadable` = invariant §2).
  - `src/api/client.ts` — `FileApi` avec **`fetch` injectable** (header `X-API-Key`) ; **seul endroit** à adapter si le JSON backend diffère du contrat (spec §4).
  - `src/api/hooks.ts` — hooks react-query (`useStats`, `useFiles`, `useUploadFiles` = boucle register+PUT par fichier, `useRescan`) + `queryClient` + `api` partagés.
  - `src/components/*` — présentation (Header, StatCards, StatusBadge, SearchBar, Pagination, FileTable, UploadModal, FileReportModal, Toast).
  - `test/mocks/*` — **faux backend MSW en mémoire** (store + machine à états de scan) qui sert **aux tests ET à la démo navigateur hors ligne** (analogue frontend des adapters in-memory). `seedDemo()` charge 8 fichiers d'exemple. `public/mockServiceWorker.js` requis pour la démo (généré par `npx msw init public/`).

## Décisions clés
- **Contract-first** : le frontend cible le **contrat §4**, MSW l'encode → dev/test **hors ligne** pendant que le backend se construit en parallèle.
- **Multi-fichiers** = boucle `POST /api/files` (un ticket + un PUT par fichier). `/api/batches` réservé au système-à-système.
- **Téléchargement** : compromis démo — le client récupère les octets via `/content` puis déclenche l'enregistrement. Évolution : backend renvoie l'URL signée en JSON (offload GCS).
- **Bootstrap `main.tsx` fail-open** : le worker MSW est en `try/catch` + `enableMockIfNeeded().finally(render)` → React monte toujours (un worker absent/en échec ne blanchit pas la page). `VITE_USE_MOCK` défaut `true`.
- **Design** : reproduit **fidèlement** la maquette Cloud Design `Fichiers sécurisés.dc.html` (copie : `docs/design/Fichiers-securises.dc.html`). Palette bleu `#005EA8` / vert `#28A15E`, styles **inline** copiés de la maquette. **En-tête = marque + bouton « Déposer un fichier »** : le bloc utilisateur fictif de la maquette (avatar + nom/société) a été **retiré** à la demande de l'utilisateur (PR #17 ; config `VITE_CLIENT_NAME`/`VITE_CLIENT_ORG` supprimée). Modale de rapport : moteur = constante `ClamAV` affichée une fois `scannedAt` présent (l'API n'expose pas le moteur par fichier) ; menace via `threatName`. Barre de progression du scan **indéterminée** (le backend expose un statut, pas un %).

## Config (env `VITE_*`)
`VITE_API_BASE_URL`, `VITE_API_KEY`, `VITE_PORTAL_NAME` (défaut `Praxedo`), `VITE_USE_MOCK` (`true`), `VITE_POLL_MS` (`2500`). Lues une fois dans `src/config.ts` (`readConfig(env)` testable).

## Mise à jour 2026-07-13 — aligné sur le contrat backend RÉEL (PR #18 `task/frontend-real-api`)
Décision utilisateur : **adapter le frontend au backend** (backend intact). Forme JSON **vérifiée en direct** (curl contre le backend en profil `local`) :
- **`FileView`** = `{ id, filename, contentType, sizeBytes, status, infected, threatName, createdAt, scannedAt }` — verdict **aplati** (`infected`+`threatName`), plus d'objet `scanVerdict`, plus de `size`/`batchId`/`updatedAt`/`sha256`. Statut sérialisé en **string**, `Instant` en **ISO**.
- **`PageResult`** = `{ items, page, size, totalElements }` — **pas de `totalPages`** dans le JSON (la méthode `totalPages()` du record Java n'est pas sérialisée par Jackson) → le client le **dérive** (`ceil(totalElements/size)`).
- **`POST /api/files`** (register) → `{ id, status, uploadUrl, uploadExpiresAt }` — **pas de `filename`** ; le body de requête garde `size` (pas `sizeBytes`).
- Le **mock MSW** (`test/mocks/store.ts`) a été réécrit pour refléter cette forme → reste une doublure fidèle (36 tests verts).
- **Piège scan en local** : le proxy `/api/_local/upload` (profils local/test) **stocke** les octets mais **ne déclenche pas** le scan → fichier reste `PENDING`. Déclenchement via `POST /rescan` (bouton « Relancer l'analyse ») ; l'**auto-trigger** (notification GCS `OBJECT_FINALIZE` → Pub/Sub → `/internal/scan-events`) n'existe qu'en profil **gcp**/déployé. Voir [[infra]].
- Clé API en local : **régénérée à chaque démarrage** du backend, loguée `Cle API de demo (profil local) : pk_...` → à copier dans `VITE_API_KEY`.

## Pièges
- La maquette `.dc.html` s'importe via le **MCP `DesignSync`** (`get_project`/`list_files`/`get_file`) depuis le projet claude.ai `0ca3fcaf-d2d5-4c77-929b-ea359660b4f5`.
- **Accents** : autorisés (et requis) dans le **contenu des fichiers source** (libellés UI) ; la contrainte ASCII ne vise QUE les commandes shell et messages de commit.
- Tests déterministes : dates formatées en **UTC** ; `test.env` dans `vite.config.ts` fixe `VITE_API_BASE_URL=http://api.test` (pour matcher les handlers MSW) + poll rapide.
- Voir [[bonnes-pratiques]] (testabilité/DI) et [[decisions-archi]] (D10 frontend).
