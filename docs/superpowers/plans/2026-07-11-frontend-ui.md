# Frontend `praxedo-upload-ui` (Jalon 4) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Construire la SPA React « Fichiers sécurisés » (`praxedo-upload-ui`) qui démontre le flux complet — dépôt de fichiers, liste live avec statuts, téléchargement **gated CLEAN**, rapport antivirus, relance de scan — contre le **contrat d'API de la spec §4**, entièrement développable et testable **hors ligne** grâce à un faux backend MSW.

**Architecture:** SPA React + TypeScript, une page (`App`). Couches découplées façon le backend : **logique pure** (`lib/format.ts`, sans React) + **client d'API** (`api/client.ts`, `fetch` injectable) testés en isolation ; **hooks TanStack Query** pour les données et le polling ; **composants** de présentation. Un **faux backend MSW** (`test/mocks`) implémente le contrat §4 et sert **à la fois** aux tests et au mode démo hors ligne (analogue frontend des adapters in-memory du backend). Le frontend cible le **contrat** (spec §4), pas une implémentation backend précise (backend construit en parallèle) : toute divergence de nommage JSON se corrige au seul endroit `api/client.ts`.

**Tech Stack:** React 18, Vite 5, TypeScript 5, `@tanstack/react-query` v5, Vitest, `@testing-library/react` + `@testing-library/user-event` + `jest-dom`, `msw` v2, `jsdom`. Aucun state manager lourd. Auth par header `X-API-Key`.

---

## Principes appliqués (cohérents avec le backend — voir memory `bonnes-pratiques`)

- **Découplage du framework** : la logique (formatage, mapping de statut) et l'accès réseau (`ApiClient`) sont des unités **testables sans React**. `fetch` est **injecté** (jamais capturé en dur).
- **Contrat d'abord** : types TS = contrat §4. MSW encode ce contrat → dev + tests hors ligne (backend en parallèle).
- **YAGNI / sans sur-ingénierie** : pas de Redux/Zustand ; TanStack Query suffit. Multi-fichiers = boucle sur `POST /api/files` (l'endpoint `/api/batches` reste réservé à l'intégration système-à-système, hors UI).
- **Invariant central (spec §2)** : l'action **Télécharger** n'apparaît **que** si `status === CLEAN`. Appliqué à un seul endroit (`isDownloadable`).
- **Config typée** via variables d'env Vite (`VITE_*`), lues une fois dans `config.ts`.
- **Accessibilité de base** : modales fermables (Échap + fond), focus initial, `aria-label`.
- Commits fréquents, un commit par étape logique.

## Machine à états (rappel spec §5) — présentation UI

| Statut | Badge (label) | Ton (classe) | Téléchargeable |
|---|---|---|---|
| `CLEAN` | Validé | `clean` (vert) | **oui** |
| `SCANNING` | Scan en cours | `scanning` (bleu) | non |
| `PENDING` | En attente | `pending` (gris) | non |
| `INFECTED` | Bloqué | `blocked` (rouge) | non |
| `SCAN_FAILED` | Échec du scan | `blocked` (rouge) | non |
| `EXPIRED` | Expiré | `pending` (gris) | non |

## Contrat d'API ciblé (spec §4) — vue frontend

- `GET /api/files/stats` → `{ total, clean, scanning, pending, blocked }`
- `GET /api/files?page&size&q&status&batchId` → `{ items: FileView[], page, totalPages, totalElements }`
- `GET /api/files/{id}` → `FileView`
- `POST /api/files` `{ filename, contentType, size }` → `201 { id, filename, status, uploadUrl, uploadExpiresAt }`
- `PUT <uploadUrl>` (octets bruts) → `200/204` (URL signée GCS en prod ; endpoint proxy en local ; URL mock en test)
- `GET /api/files/{id}/content` → `302` URL signée si `CLEAN`, sinon `403`
- `POST /api/files/{id}/rescan` → `202`

Header d'auth sur toutes les routes `/api` : `X-API-Key: <clé>`.

---

## Carte des fichiers

```
praxedo-upload-ui/
  package.json            deps + scripts (dev/build/test/lint)
  vite.config.ts          plugin React + config Vitest (jsdom, setup)
  tsconfig.json           TS strict
  tsconfig.node.json      TS pour la config Vite
  index.html              point d'entree Vite
  .gitignore              node_modules, dist, .env.local, coverage
  .env.example            VITE_API_BASE_URL / VITE_API_KEY / VITE_PORTAL_NAME / VITE_USE_MOCK / VITE_POLL_MS
  README.md               choix techniques, hypotheses, run, pistes (Task 13)
  src/
    main.tsx              bootstrap : QueryClientProvider (+ worker MSW si mock), <App/>
    App.tsx               page unique : Header + StatCards + SearchBar + FileTable + modales
    config.ts             readConfig(env) -> AppConfig ; export const config
    styles.css            variables palette Praxedo + styles globaux
    api/
      types.ts            FileStatus, FileView, ScanVerdict, PageResult, StatsView, UploadTicket, FileQuery
      client.ts           ApiError + FileApi + createFileApi(config, fetchImpl)
      hooks.ts            QueryClient, useStats, useFiles, useUploadFiles, useRescan, useDownload
    lib/
      format.ts           formatBytes, formatDate, statusPresentation, isDownloadable
    components/
      Modal.tsx           coquille de modale accessible (Echap, backdrop, aria)
      StatusBadge.tsx     status -> badge (via statusPresentation)
      Header.tsx          marque + portalName + cle API masquee
      StatCards.tsx       4 cartes de metriques depuis StatsView
      SearchBar.tsx       champ recherche (debounce) + filtre statut
      Pagination.tsx      precedent/suivant + "page X / Y"
      FileTable.tsx       NOM/TAILLE/AJOUTE LE/STATUT/ACTION + etat vide
      UploadModal.tsx     drag & drop + picker multi-fichiers -> flux d'upload
      FileReportModal.tsx rapport antivirus (verdict + telecharger/rescanner)
  test/
    setup.ts              jest-dom + demarrage/arret du serveur MSW
    mocks/
      store.ts            faux backend en memoire (etat + machine a etats de scan)
      handlers.ts         handlers MSW implementant le contrat §4
      server.ts           setupServer (tests Node)
      browser.ts          setupWorker (mode mock navigateur)
```

## Ordonnancement (DAG — une branche + une PR par tâche vers `develop`)

```
T1 Scaffold ─┬─ T2 types+config ─┬─ T3 format(TDD) ────┐
             │                   ├─ T4 client(TDD) ─┐   │
             └─ T5 MSW mock ─────┘                  ├─ T6 hooks ─┐
                                                    │            │
T7 Modal+StatusBadge ──────────────────────────────┘            │
T8 Header+StatCards  (dep T2,T3)                                 │
T9 SearchBar+Pagination+FileTable (dep T3,T7)                    ├─ T12 App+wiring ─ T13 README
T10 UploadModal (dep T6,T7)                                      │
T11 FileReportModal (dep T3,T4,T6,T7)                            │
```
- **Parallélisables** après T6 : T7, T8, T9, T10, T11 (fichiers disjoints).
- T12 assemble tout ; T13 documente. T3/T4/T5 parallélisables après T2.

---

## Task 1: Scaffold du projet (Vite + React + TS + Vitest + MSW)

**Files:**
- Create: `package.json`, `vite.config.ts`, `tsconfig.json`, `tsconfig.node.json`, `index.html`, `.gitignore`, `.env.example`, `src/main.tsx`, `src/App.tsx`, `src/styles.css`, `test/setup.ts`
- Test: `src/smoke.test.tsx`

- [ ] **Step 1: `package.json`**

```json
{
  "name": "praxedo-upload-ui",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "preview": "vite preview",
    "test": "vitest run",
    "test:watch": "vitest",
    "lint": "tsc -b --noEmit"
  },
  "dependencies": {
    "@tanstack/react-query": "^5.51.0",
    "react": "^18.3.1",
    "react-dom": "^18.3.1"
  },
  "devDependencies": {
    "@testing-library/jest-dom": "^6.4.6",
    "@testing-library/react": "^16.0.0",
    "@testing-library/user-event": "^14.5.2",
    "@types/react": "^18.3.3",
    "@types/react-dom": "^18.3.0",
    "@vitejs/plugin-react": "^4.3.1",
    "jsdom": "^24.1.0",
    "msw": "^2.3.1",
    "typescript": "^5.5.3",
    "vite": "^5.3.3",
    "vitest": "^2.0.2"
  }
}
```

- [ ] **Step 2: `tsconfig.json` + `tsconfig.node.json`**

`tsconfig.json` :
```json
{
  "compilerOptions": {
    "target": "ES2021",
    "useDefineForClassFields": true,
    "lib": ["ES2021", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "types": ["vitest/globals", "@testing-library/jest-dom"]
  },
  "include": ["src", "test"],
  "references": [{ "path": "./tsconfig.node.json" }]
}
```
`tsconfig.node.json` :
```json
{
  "compilerOptions": {
    "composite": true,
    "skipLibCheck": true,
    "module": "ESNext",
    "moduleResolution": "bundler",
    "allowSyntheticDefaultImports": true,
    "strict": true,
    "noEmit": true
  },
  "include": ["vite.config.ts"]
}
```

- [ ] **Step 3: `vite.config.ts`** (config app + Vitest)

```ts
/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./test/setup.ts'],
    css: false,
  },
});
```

- [ ] **Step 4: `index.html`**

```html
<!doctype html>
<html lang="fr">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Fichiers securises</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

- [ ] **Step 5: `.gitignore` + `.env.example`**

`.gitignore` :
```
node_modules
dist
dist-ssr
coverage
*.local
.env
.env.local
.DS_Store
```
`.env.example` :
```
# URL de base de l'API backend (sans slash final)
VITE_API_BASE_URL=http://localhost:8080
# Cle API par-client (header X-API-Key). En prod : injectee au build/deploy, jamais commitee.
VITE_API_KEY=dev-local-key
# Nom du portail affiche dans l'en-tete
VITE_PORTAL_NAME=Praxedo - Fichiers securises
# true => faux backend MSW dans le navigateur (demo hors ligne). false => vrai backend.
VITE_USE_MOCK=true
# Intervalle de polling des statuts (ms)
VITE_POLL_MS=2500
```

- [ ] **Step 6: `src/styles.css`** (palette Praxedo bleu/vert, variante claire)

```css
:root {
  --brand: #1a4f8b;            /* bleu corporate */
  --brand-dark: #143b68;
  --accent: #17a06e;           /* vert */
  --bg: #f4f6f9;
  --surface: #ffffff;
  --text: #1c2733;
  --muted: #64748b;
  --border: #e2e8f0;
  --clean-bg: #e6f6ee; --clean-fg: #0f7a4f;
  --scanning-bg: #e6f0fb; --scanning-fg: #1a4f8b;
  --pending-bg: #eef1f5; --pending-fg: #64748b;
  --blocked-bg: #fdecec; --blocked-fg: #c0341d;
  --radius: 10px;
  --shadow: 0 1px 3px rgba(16,32,64,.08), 0 4px 12px rgba(16,32,64,.06);
  font-family: system-ui, -apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
}
* { box-sizing: border-box; }
body { margin: 0; background: var(--bg); color: var(--text); }
button { font: inherit; cursor: pointer; }
.app { max-width: 1040px; margin: 0 auto; padding: 24px 20px 64px; }
.card { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius); box-shadow: var(--shadow); }
.btn { border: 1px solid var(--border); background: var(--surface); border-radius: 8px; padding: 8px 14px; color: var(--text); }
.btn-primary { background: var(--brand); border-color: var(--brand); color: #fff; }
.btn-primary:hover { background: var(--brand-dark); }
.btn:disabled { opacity: .5; cursor: not-allowed; }
.badge { display: inline-flex; align-items: center; gap: 6px; padding: 3px 10px; border-radius: 999px; font-size: .82rem; font-weight: 600; }
.badge.clean { background: var(--clean-bg); color: var(--clean-fg); }
.badge.scanning { background: var(--scanning-bg); color: var(--scanning-fg); }
.badge.pending { background: var(--pending-bg); color: var(--pending-fg); }
.badge.blocked { background: var(--blocked-bg); color: var(--blocked-fg); }
.stat-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin: 20px 0; }
.stat { padding: 16px 18px; }
.stat .value { font-size: 1.9rem; font-weight: 700; }
.stat .label { color: var(--muted); font-size: .85rem; text-transform: uppercase; letter-spacing: .04em; }
table { width: 100%; border-collapse: collapse; }
th { text-align: left; font-size: .78rem; text-transform: uppercase; letter-spacing: .04em; color: var(--muted); padding: 12px 14px; }
td { padding: 12px 14px; border-top: 1px solid var(--border); }
.modal-backdrop { position: fixed; inset: 0; background: rgba(16,32,64,.45); display: flex; align-items: center; justify-content: center; padding: 20px; z-index: 50; }
.modal { max-width: 520px; width: 100%; padding: 22px; }
.dropzone { border: 2px dashed var(--border); border-radius: var(--radius); padding: 32px; text-align: center; color: var(--muted); }
.dropzone.over { border-color: var(--brand); background: var(--scanning-bg); }
.spin { display: inline-block; width: 12px; height: 12px; border: 2px solid currentColor; border-top-color: transparent; border-radius: 50%; animation: spin .8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: 720px) { .stat-grid { grid-template-columns: repeat(2, 1fr); } }
```

- [ ] **Step 7: `test/setup.ts`** (placeholder MSW — serveur ajouté en T5)

```ts
import '@testing-library/jest-dom/vitest';
// Le serveur MSW est branche ici en Task 5 (server.listen/resetHandlers/close).
```

- [ ] **Step 8: `src/App.tsx` + `src/main.tsx` (versions minimales — remplacees en T12)**

`src/App.tsx` :
```tsx
export default function App() {
  return <div className="app"><h1>Fichiers securises</h1></div>;
}
```
`src/main.tsx` :
```tsx
import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './styles.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
```

- [ ] **Step 9: `src/smoke.test.tsx`**

```tsx
import { render, screen } from '@testing-library/react';
import App from './App';

test('affiche le titre de la page', () => {
  render(<App />);
  expect(screen.getByRole('heading', { name: /fichiers securises/i })).toBeInTheDocument();
});
```

- [ ] **Step 10: Installer + lancer les tests**

Run: `cd praxedo-upload-ui && npm install && npm test`
Expected: 1 test PASS (`smoke.test.tsx`).

- [ ] **Step 11: Commit**

```bash
git add -A
git commit -m "chore: scaffold Vite React TS project with Vitest and MSW deps"
```

---

## Task 2: Types du domaine + configuration

**Files:**
- Create: `src/api/types.ts`, `src/config.ts`
- Test: `src/config.test.ts`

- [ ] **Step 1: `src/api/types.ts`**

```ts
export type FileStatus =
  | 'PENDING' | 'SCANNING' | 'CLEAN' | 'INFECTED' | 'SCAN_FAILED' | 'EXPIRED';

export interface ScanVerdict {
  engine: string | null;
  verdict: 'CLEAN' | 'INFECTED' | null;
  threatName: string | null;
  scannedAt: string | null;
}

export interface FileView {
  id: string;
  filename: string;
  contentType: string;
  size: number;                 // octets
  status: FileStatus;
  batchId: string | null;
  scanVerdict: ScanVerdict | null;
  createdAt: string;            // ISO 8601
  updatedAt: string;
  scannedAt: string | null;
}

export interface PageResult<T> {
  items: T[];
  page: number;
  totalPages: number;
  totalElements: number;
}

export interface StatsView {
  total: number;
  clean: number;
  scanning: number;
  pending: number;
  blocked: number;
}

export interface UploadTicket {
  id: string;
  filename: string;
  status: FileStatus;
  uploadUrl: string;
  uploadExpiresAt: string;
}

export interface FileQuery {
  page?: number;
  size?: number;
  q?: string;
  status?: FileStatus | '';
  batchId?: string;
}
```

- [ ] **Step 2: Write the failing test `src/config.test.ts`**

```ts
import { readConfig } from './config';

test('lit la config depuis les variables VITE_ avec valeurs par defaut', () => {
  const cfg = readConfig({
    VITE_API_BASE_URL: 'http://api.test',
    VITE_API_KEY: 'k',
    VITE_PORTAL_NAME: 'Portail',
    VITE_USE_MOCK: 'false',
    VITE_POLL_MS: '3000',
  } as unknown as ImportMetaEnv);
  expect(cfg).toEqual({
    apiBaseUrl: 'http://api.test',
    apiKey: 'k',
    portalName: 'Portail',
    useMock: false,
    pollIntervalMs: 3000,
  });
});

test('retire le slash final et applique les defauts', () => {
  const cfg = readConfig({ VITE_API_BASE_URL: 'http://api.test/' } as unknown as ImportMetaEnv);
  expect(cfg.apiBaseUrl).toBe('http://api.test');
  expect(cfg.useMock).toBe(true);          // defaut : mock actif
  expect(cfg.pollIntervalMs).toBe(2500);
  expect(cfg.portalName).toBe('Fichiers securises');
});
```

- [ ] **Step 3: Run test to verify it fails**

Run: `npm test -- config`
Expected: FAIL (`readConfig` not exported).

- [ ] **Step 4: `src/config.ts`**

```ts
export interface AppConfig {
  apiBaseUrl: string;
  apiKey: string;
  portalName: string;
  useMock: boolean;
  pollIntervalMs: number;
}

export function readConfig(env: ImportMetaEnv): AppConfig {
  const trimSlash = (u: string) => u.replace(/\/+$/, '');
  return {
    apiBaseUrl: trimSlash(env.VITE_API_BASE_URL ?? 'http://localhost:8080'),
    apiKey: env.VITE_API_KEY ?? 'dev-local-key',
    portalName: env.VITE_PORTAL_NAME ?? 'Fichiers securises',
    useMock: (env.VITE_USE_MOCK ?? 'true') !== 'false',
    pollIntervalMs: Number(env.VITE_POLL_MS ?? '2500'),
  };
}

export const config = readConfig(import.meta.env);
```

- [ ] **Step 5: Run test to verify it passes**

Run: `npm test -- config`
Expected: 2 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add src/api/types.ts src/config.ts src/config.test.ts
git commit -m "feat: add domain types and typed app config"
```

---

## Task 3: Logique de présentation pure (`lib/format.ts`) — TDD

**Files:**
- Create: `src/lib/format.ts`
- Test: `src/lib/format.test.ts`

- [ ] **Step 1: Write failing tests `src/lib/format.test.ts`**

```ts
import { formatBytes, formatDate, statusPresentation, isDownloadable } from './format';

test('formatBytes en unites francaises (base 1000)', () => {
  expect(formatBytes(0)).toBe('0 o');
  expect(formatBytes(920)).toBe('920 o');
  expect(formatBytes(18_000_000)).toBe('18 Mo');
  expect(formatBytes(2_400_000)).toBe('2,4 Mo');
  expect(formatBytes(1_100_000_000)).toBe('1,1 Go');
});

test('formatDate en francais court et deterministe', () => {
  expect(formatDate('2026-07-11T09:30:00Z')).toMatch(/2026/);
  expect(formatDate('2026-07-11T09:30:00Z')).toMatch(/juil/i);
});

test('statusPresentation mappe statut -> label + ton', () => {
  expect(statusPresentation('CLEAN')).toEqual({ label: 'Valide', tone: 'clean' });
  expect(statusPresentation('SCANNING')).toEqual({ label: 'Scan en cours', tone: 'scanning' });
  expect(statusPresentation('PENDING')).toEqual({ label: 'En attente', tone: 'pending' });
  expect(statusPresentation('INFECTED')).toEqual({ label: 'Bloque', tone: 'blocked' });
  expect(statusPresentation('SCAN_FAILED')).toEqual({ label: 'Echec du scan', tone: 'blocked' });
  expect(statusPresentation('EXPIRED')).toEqual({ label: 'Expire', tone: 'pending' });
});

test('isDownloadable vrai uniquement pour CLEAN (invariant spec §2)', () => {
  expect(isDownloadable('CLEAN')).toBe(true);
  for (const s of ['PENDING', 'SCANNING', 'INFECTED', 'SCAN_FAILED', 'EXPIRED'] as const) {
    expect(isDownloadable(s)).toBe(false);
  }
});
```

- [ ] **Step 2: Run to verify it fails**

Run: `npm test -- format`
Expected: FAIL (module not found).

- [ ] **Step 3: `src/lib/format.ts`**

```ts
import type { FileStatus } from '../api/types';

const UNITS = ['o', 'Ko', 'Mo', 'Go', 'To'];

export function formatBytes(bytes: number): string {
  if (bytes < 1000) return `${bytes} o`;
  let value = bytes;
  let i = 0;
  while (value >= 1000 && i < UNITS.length - 1) {
    value /= 1000;
    i++;
  }
  const rounded = Math.round(value * 10) / 10;
  const text = Number.isInteger(rounded)
    ? String(rounded)
    : rounded.toFixed(1).replace('.', ',');
  return `${text} ${UNITS[i]}`;
}

const DATE_FMT = new Intl.DateTimeFormat('fr-FR', {
  day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC',
});

export function formatDate(iso: string): string {
  return DATE_FMT.format(new Date(iso));
}

export type BadgeTone = 'clean' | 'scanning' | 'pending' | 'blocked';

const PRESENTATION: Record<FileStatus, { label: string; tone: BadgeTone }> = {
  CLEAN: { label: 'Valide', tone: 'clean' },
  SCANNING: { label: 'Scan en cours', tone: 'scanning' },
  PENDING: { label: 'En attente', tone: 'pending' },
  INFECTED: { label: 'Bloque', tone: 'blocked' },
  SCAN_FAILED: { label: 'Echec du scan', tone: 'blocked' },
  EXPIRED: { label: 'Expire', tone: 'pending' },
};

export function statusPresentation(status: FileStatus) {
  return PRESENTATION[status];
}

export function isDownloadable(status: FileStatus): boolean {
  return status === 'CLEAN';
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `npm test -- format`
Expected: 4 tests PASS.

> Note d'accents : les labels utilisent des caracteres ASCII (`Valide`, `Bloque`) pour rester coherent avec la contrainte du validateur de commandes du projet ; en T13 on documente qu'un vrai rendu accentue est trivial (les chaines UI peuvent porter les accents, seuls les messages shell/commits restent ASCII).

- [ ] **Step 5: Commit**

```bash
git add src/lib/format.ts src/lib/format.test.ts
git commit -m "feat: add pure presentation helpers (format + status mapping)"
```

---

## Task 4: Client d'API (`api/client.ts`) — `fetch` injectable, TDD

**Files:**
- Create: `src/api/client.ts`
- Test: `src/api/client.test.ts`

- [ ] **Step 1: Write failing tests `src/api/client.test.ts`**

```ts
import { createFileApi, ApiError } from './client';
import type { AppConfig } from '../config';

const cfg: AppConfig = {
  apiBaseUrl: 'http://api.test', apiKey: 'secret',
  portalName: 'P', useMock: false, pollIntervalMs: 1000,
};

function fakeFetch(handler: (url: string, init?: RequestInit) => Response) {
  const calls: { url: string; init?: RequestInit }[] = [];
  const fn = async (url: string | URL, init?: RequestInit) => {
    calls.push({ url: String(url), init });
    return handler(String(url), init);
  };
  return { fn: fn as unknown as typeof fetch, calls };
}

test('getStats envoie X-API-Key et parse le corps', async () => {
  const { fn, calls } = fakeFetch(() =>
    new Response(JSON.stringify({ total: 3, clean: 1, scanning: 1, pending: 1, blocked: 0 }),
      { status: 200, headers: { 'content-type': 'application/json' } }));
  const api = createFileApi(cfg, fn);
  const stats = await api.getStats();
  expect(stats.total).toBe(3);
  expect(calls[0].url).toBe('http://api.test/api/files/stats');
  expect((calls[0].init!.headers as Record<string, string>)['X-API-Key']).toBe('secret');
});

test('listFiles serialise les parametres de requete', async () => {
  const { fn, calls } = fakeFetch(() =>
    new Response(JSON.stringify({ items: [], page: 0, totalPages: 0, totalElements: 0 }),
      { status: 200, headers: { 'content-type': 'application/json' } }));
  const api = createFileApi(cfg, fn);
  await api.listFiles({ page: 1, size: 6, q: 'rap', status: 'CLEAN' });
  const u = new URL(calls[0].url);
  expect(u.pathname).toBe('/api/files');
  expect(u.searchParams.get('page')).toBe('1');
  expect(u.searchParams.get('size')).toBe('6');
  expect(u.searchParams.get('q')).toBe('rap');
  expect(u.searchParams.get('status')).toBe('CLEAN');
});

test('registerUpload POST le corps JSON', async () => {
  const { fn, calls } = fakeFetch(() =>
    new Response(JSON.stringify({ id: 'f1', filename: 'a.pdf', status: 'PENDING', uploadUrl: 'http://up/f1', uploadExpiresAt: 'x' }),
      { status: 201, headers: { 'content-type': 'application/json' } }));
  const api = createFileApi(cfg, fn);
  const ticket = await api.registerUpload({ filename: 'a.pdf', contentType: 'application/pdf', size: 10 });
  expect(ticket.uploadUrl).toBe('http://up/f1');
  expect(calls[0].init!.method).toBe('POST');
  expect(JSON.parse(calls[0].init!.body as string)).toEqual({ filename: 'a.pdf', contentType: 'application/pdf', size: 10 });
});

test('uploadBytes PUT les octets sans X-API-Key (URL deja signee)', async () => {
  const { fn, calls } = fakeFetch(() => new Response(null, { status: 200 }));
  const api = createFileApi(cfg, fn);
  const file = new File(['hello'], 'a.txt', { type: 'text/plain' });
  await api.uploadBytes('http://up/f1', file);
  expect(calls[0].init!.method).toBe('PUT');
  expect(calls[0].url).toBe('http://up/f1');
  expect((calls[0].init!.headers as Record<string, string>)['X-API-Key']).toBeUndefined();
});

test('rescan POST /rescan', async () => {
  const { fn, calls } = fakeFetch(() => new Response(null, { status: 202 }));
  const api = createFileApi(cfg, fn);
  await api.rescan('f1');
  expect(calls[0].url).toBe('http://api.test/api/files/f1/rescan');
  expect(calls[0].init!.method).toBe('POST');
});

test('une reponse non-OK leve ApiError avec le status', async () => {
  const { fn } = fakeFetch(() => new Response('boom', { status: 403 }));
  const api = createFileApi(cfg, fn);
  await expect(api.getFile('f1')).rejects.toMatchObject({ status: 403 });
  await expect(api.getFile('f1')).rejects.toBeInstanceOf(ApiError);
});
```

- [ ] **Step 2: Run to verify it fails**

Run: `npm test -- client`
Expected: FAIL (module not found).

- [ ] **Step 3: `src/api/client.ts`**

```ts
import type { AppConfig } from '../config';
import type { FileView, PageResult, StatsView, UploadTicket, FileQuery } from './types';

export class ApiError extends Error {
  constructor(public status: number, message: string, public body?: string) {
    super(message);
    this.name = 'ApiError';
  }
}

export interface RegisterUploadInput {
  filename: string;
  contentType: string;
  size: number;
}

export interface FileApi {
  getStats(): Promise<StatsView>;
  listFiles(query: FileQuery): Promise<PageResult<FileView>>;
  getFile(id: string): Promise<FileView>;
  registerUpload(input: RegisterUploadInput): Promise<UploadTicket>;
  uploadBytes(uploadUrl: string, file: File): Promise<void>;
  rescan(id: string): Promise<void>;
  downloadFile(id: string, filename: string): Promise<void>;
}

export function createFileApi(config: AppConfig, fetchImpl: typeof fetch = fetch): FileApi {
  const base = config.apiBaseUrl;
  const authHeaders = (): Record<string, string> => ({ 'X-API-Key': config.apiKey });

  async function request<T>(path: string, init?: RequestInit): Promise<T> {
    const res = await fetchImpl(`${base}${path}`, {
      ...init,
      headers: { ...authHeaders(), ...(init?.headers ?? {}) },
    });
    if (!res.ok) {
      const body = await res.text().catch(() => '');
      throw new ApiError(res.status, `HTTP ${res.status} on ${path}`, body);
    }
    if (res.status === 204) return undefined as T;
    return (await res.json()) as T;
  }

  return {
    getStats: () => request<StatsView>('/api/files/stats'),

    listFiles: (query) => {
      const p = new URLSearchParams();
      if (query.page != null) p.set('page', String(query.page));
      if (query.size != null) p.set('size', String(query.size));
      if (query.q) p.set('q', query.q);
      if (query.status) p.set('status', query.status);
      if (query.batchId) p.set('batchId', query.batchId);
      const qs = p.toString();
      return request<PageResult<FileView>>(`/api/files${qs ? `?${qs}` : ''}`);
    },

    getFile: (id) => request<FileView>(`/api/files/${encodeURIComponent(id)}`),

    registerUpload: (input) =>
      request<UploadTicket>('/api/files', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(input),
      }),

    uploadBytes: async (uploadUrl, file) => {
      // URL deja signee (GCS) ou proxy local : PAS de X-API-Key ici.
      const res = await fetchImpl(uploadUrl, {
        method: 'PUT',
        headers: { 'Content-Type': file.type || 'application/octet-stream' },
        body: file,
      });
      if (!res.ok) throw new ApiError(res.status, `Upload PUT failed (${res.status})`);
    },

    rescan: (id) =>
      request<void>(`/api/files/${encodeURIComponent(id)}/rescan`, { method: 'POST' }),

    downloadFile: async (id, filename) => {
      // GET /content -> 302 URL signee ; en dev/mock le fetch suit la redirection.
      // Compromis demo : on recupere les octets puis on declenche l'enregistrement.
      // Evolution (README §pistes) : le backend renvoie l'URL signee en JSON pour
      // que le navigateur telecharge directement depuis GCS (offload des octets).
      const res = await fetchImpl(`${base}/api/files/${encodeURIComponent(id)}/content`, {
        headers: authHeaders(),
      });
      if (!res.ok) throw new ApiError(res.status, `Download failed (${res.status})`);
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    },
  };
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `npm test -- client`
Expected: all tests PASS (`downloadFile` non testé ici — couvert via MSW en T11).

- [ ] **Step 5: Commit**

```bash
git add src/api/client.ts src/api/client.test.ts
git commit -m "feat: add FileApi client with injectable fetch and X-API-Key auth"
```

---

## Task 5: Faux backend MSW (`test/mocks`) — contrat §4 en mémoire

**Files:**
- Create: `test/mocks/store.ts`, `test/mocks/handlers.ts`, `test/mocks/server.ts`, `test/mocks/browser.ts`
- Modify: `test/setup.ts`
- Test: `test/mocks/handlers.test.ts`

- [ ] **Step 1: `test/mocks/store.ts`** (état + machine à états de scan)

```ts
import type { FileView, FileStatus, StatsView } from '../../src/api/types';

const now = () => new Date('2026-07-11T09:00:00Z').toISOString();

interface StoreFile extends FileView {
  revealAtPoll: number;   // le scan se resout au polling >= cette valeur
  uploaded: boolean;
}

let files: StoreFile[] = [];
let pollCount = 0;
let seq = 0;

function isThreat(filename: string): boolean {
  return /eicar|virus|infected|malware/i.test(filename);
}

export function resetStore(seed: Partial<StoreFile>[] = []): void {
  files = [];
  pollCount = 0;
  seq = 0;
  for (const s of seed) addSeed(s);
}

function addSeed(s: Partial<StoreFile>): void {
  seq++;
  files.push({
    id: s.id ?? `seed-${seq}`,
    filename: s.filename ?? `file-${seq}.txt`,
    contentType: s.contentType ?? 'text/plain',
    size: s.size ?? 1024,
    status: s.status ?? 'CLEAN',
    batchId: s.batchId ?? null,
    scanVerdict: s.scanVerdict ?? null,
    createdAt: s.createdAt ?? now(),
    updatedAt: s.updatedAt ?? now(),
    scannedAt: s.scannedAt ?? null,
    revealAtPoll: s.revealAtPoll ?? 0,
    uploaded: s.uploaded ?? true,
  });
}

export function register(input: { filename: string; contentType: string; size: number }) {
  seq++;
  const id = `f-${seq}`;
  files.push({
    id, filename: input.filename, contentType: input.contentType, size: input.size,
    status: 'PENDING', batchId: null, scanVerdict: null,
    createdAt: now(), updatedAt: now(), scannedAt: null,
    revealAtPoll: Number.MAX_SAFE_INTEGER, uploaded: false,
  });
  return { id, filename: input.filename, status: 'PENDING' as FileStatus, uploadUrl: `http://mock.local/upload/${id}`, uploadExpiresAt: now() };
}

export function markUploaded(id: string): boolean {
  const f = files.find((x) => x.id === id);
  if (!f) return false;
  f.uploaded = true;
  f.status = 'SCANNING';
  f.revealAtPoll = pollCount + 1;    // visible en SCANNING au moins un tick
  f.updatedAt = now();
  return true;
}

function advance(f: StoreFile): void {
  if (f.status === 'SCANNING' && pollCount >= f.revealAtPoll) {
    if (isThreat(f.filename)) {
      f.status = 'INFECTED';
      f.scanVerdict = { engine: 'ClamAV(mock)', verdict: 'INFECTED', threatName: 'Eicar-Test-Signature', scannedAt: now() };
    } else {
      f.status = 'CLEAN';
      f.scanVerdict = { engine: 'ClamAV(mock)', verdict: 'CLEAN', threatName: null, scannedAt: now() };
    }
    f.scannedAt = now();
    f.updatedAt = now();
  }
}

function tick(): void {
  pollCount++;
  files.forEach(advance);
}

export function listFiles(params: URLSearchParams) {
  tick();
  const page = Number(params.get('page') ?? '0');
  const size = Number(params.get('size') ?? '6');
  const q = (params.get('q') ?? '').toLowerCase();
  const status = params.get('status') ?? '';
  let filtered = files.filter((f) => f.status !== 'EXPIRED');
  if (q) filtered = filtered.filter((f) => f.filename.toLowerCase().includes(q));
  if (status) filtered = filtered.filter((f) => f.status === status);
  const totalElements = filtered.length;
  const totalPages = Math.max(1, Math.ceil(totalElements / size));
  const items = filtered.slice(page * size, page * size + size).map(toView);
  return { items, page, totalPages, totalElements };
}

export function stats(): StatsView {
  tick();
  const c = (s: FileStatus) => files.filter((f) => f.status === s).length;
  return {
    total: files.length,
    clean: c('CLEAN'),
    scanning: c('SCANNING'),
    pending: c('PENDING'),
    blocked: c('INFECTED') + c('SCAN_FAILED'),
  };
}

export function getFile(id: string): FileView | undefined {
  const f = files.find((x) => x.id === id);
  return f ? toView(f) : undefined;
}

export function rescan(id: string): boolean {
  const f = files.find((x) => x.id === id);
  if (!f) return false;
  f.status = 'SCANNING';
  f.scanVerdict = null;
  f.revealAtPoll = pollCount + 1;
  f.updatedAt = now();
  return true;
}

function toView(f: StoreFile): FileView {
  const { revealAtPoll: _r, uploaded: _u, ...view } = f;
  return view;
}
```

- [ ] **Step 2: `test/mocks/handlers.ts`**

```ts
import { http, HttpResponse } from 'msw';
import * as store from './store';

const BASE = 'http://api.test';        // aligne sur cfg de test ; en navigateur, voir browser.ts

export const handlers = [
  http.get(`${BASE}/api/files/stats`, () => HttpResponse.json(store.stats())),

  http.get(`${BASE}/api/files/:id`, ({ params }) => {
    const f = store.getFile(String(params.id));
    return f ? HttpResponse.json(f) : new HttpResponse(null, { status: 404 });
  }),

  http.get(`${BASE}/api/files`, ({ request }) => {
    const url = new URL(request.url);
    return HttpResponse.json(store.listFiles(url.searchParams));
  }),

  http.post(`${BASE}/api/files`, async ({ request }) => {
    const body = (await request.json()) as { filename: string; contentType: string; size: number };
    return HttpResponse.json(store.register(body), { status: 201 });
  }),

  http.post(`${BASE}/api/files/:id/rescan`, ({ params }) =>
    store.rescan(String(params.id))
      ? new HttpResponse(null, { status: 202 })
      : new HttpResponse(null, { status: 404 })),

  http.get(`${BASE}/api/files/:id/content`, ({ params }) => {
    const f = store.getFile(String(params.id));
    if (!f) return new HttpResponse(null, { status: 404 });
    if (f.status !== 'CLEAN') return new HttpResponse(null, { status: 403 });
    return new HttpResponse(new Blob([`contenu de ${f.filename}`]), { status: 200 });
  }),

  http.put('http://mock.local/upload/:id', ({ params }) =>
    store.markUploaded(String(params.id))
      ? new HttpResponse(null, { status: 200 })
      : new HttpResponse(null, { status: 404 })),
];
```

- [ ] **Step 3: `test/mocks/server.ts` + `test/mocks/browser.ts`**

`test/mocks/server.ts` :
```ts
import { setupServer } from 'msw/node';
import { handlers } from './handlers';

export const server = setupServer(...handlers);
```
`test/mocks/browser.ts` (mode démo navigateur — utilisé en T12 par `main.tsx`) :
```ts
import { setupWorker } from 'msw/browser';
import { http, HttpResponse } from 'msw';
import * as store from './store';

// En navigateur, l'API cible config.apiBaseUrl. On genere les memes handlers
// mais relatifs a l'origine appelee ; ici on route sur n'importe quel host.
export function makeWorker(apiBaseUrl: string) {
  const B = apiBaseUrl;
  return setupWorker(
    http.get(`${B}/api/files/stats`, () => HttpResponse.json(store.stats())),
    http.get(`${B}/api/files/:id`, ({ params }) => {
      const f = store.getFile(String(params.id));
      return f ? HttpResponse.json(f) : new HttpResponse(null, { status: 404 });
    }),
    http.get(`${B}/api/files`, ({ request }) =>
      HttpResponse.json(store.listFiles(new URL(request.url).searchParams))),
    http.post(`${B}/api/files`, async ({ request }) =>
      HttpResponse.json(store.register((await request.json()) as any), { status: 201 })),
    http.post(`${B}/api/files/:id/rescan`, ({ params }) =>
      store.rescan(String(params.id)) ? new HttpResponse(null, { status: 202 }) : new HttpResponse(null, { status: 404 })),
    http.get(`${B}/api/files/:id/content`, ({ params }) => {
      const f = store.getFile(String(params.id));
      if (!f) return new HttpResponse(null, { status: 404 });
      if (f.status !== 'CLEAN') return new HttpResponse(null, { status: 403 });
      return new HttpResponse(new Blob([`contenu de ${f.filename}`]), { status: 200 });
    }),
    http.put('http://mock.local/upload/:id', ({ params }) =>
      store.markUploaded(String(params.id)) ? new HttpResponse(null, { status: 200 }) : new HttpResponse(null, { status: 404 })),
  );
}
```

- [ ] **Step 4: Modifier `test/setup.ts`**

```ts
import '@testing-library/jest-dom/vitest';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { server } from './mocks/server';
import { resetStore } from './mocks/store';

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => { server.resetHandlers(); resetStore(); });
afterAll(() => server.close());
```

- [ ] **Step 5: Write test `test/mocks/handlers.test.ts`**

```ts
import { createFileApi } from '../../src/api/client';
import type { AppConfig } from '../../src/config';
import { resetStore } from './store';

const cfg: AppConfig = { apiBaseUrl: 'http://api.test', apiKey: 'k', portalName: 'P', useMock: true, pollIntervalMs: 10 };
const api = createFileApi(cfg);

test('cycle complet : register -> upload -> SCANNING -> CLEAN', async () => {
  resetStore();
  const ticket = await api.registerUpload({ filename: 'rapport.pdf', contentType: 'application/pdf', size: 2_400_000 });
  expect(ticket.status).toBe('PENDING');
  await api.uploadBytes(ticket.uploadUrl, new File(['x'], 'rapport.pdf'));
  const p1 = await api.listFiles({ page: 0, size: 6 });   // tick 1 -> SCANNING
  expect(p1.items[0].status).toBe('SCANNING');
  const p2 = await api.listFiles({ page: 0, size: 6 });   // tick 2 -> CLEAN
  expect(p2.items[0].status).toBe('CLEAN');
});

test('un fichier eicar finit INFECTED et le download renvoie 403', async () => {
  resetStore();
  const t = await api.registerUpload({ filename: 'eicar.txt', contentType: 'text/plain', size: 68 });
  await api.uploadBytes(t.uploadUrl, new File(['x'], 'eicar.txt'));
  await api.listFiles({ page: 0, size: 6 });
  await api.listFiles({ page: 0, size: 6 });
  const f = await api.getFile(t.id);
  expect(f.status).toBe('INFECTED');
  expect(f.scanVerdict?.threatName).toBe('Eicar-Test-Signature');
  await expect(api.downloadFile(t.id, 'eicar.txt')).rejects.toMatchObject({ status: 403 });
});

test('stats reflete les compteurs', async () => {
  resetStore([{ status: 'CLEAN' }, { status: 'INFECTED' }, { status: 'PENDING' }]);
  const s = await api.getStats();
  expect(s).toMatchObject({ total: 3, clean: 1, blocked: 1, pending: 1 });
});
```

- [ ] **Step 6: Run**

Run: `npm test -- handlers`
Expected: 3 tests PASS.

- [ ] **Step 7: Commit**

```bash
git add test/mocks src/../test/setup.ts test/setup.ts
git commit -m "feat: add MSW in-memory fake backend (contract + scan state machine)"
```

---

## Task 6: Hooks TanStack Query (`api/hooks.ts`)

**Files:**
- Create: `src/api/hooks.ts`
- Test: `src/api/hooks.test.tsx`

- [ ] **Step 1: `src/api/hooks.ts`**

```tsx
import { QueryClient, useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { createFileApi } from './client';
import { config } from '../config';
import type { FileQuery, FileView, PageResult, StatsView } from './types';

export const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } },
});

export const api = createFileApi(config);

const hasActive = (items: FileView[]) =>
  items.some((f) => f.status === 'PENDING' || f.status === 'SCANNING');

export function useStats() {
  return useQuery<StatsView>({
    queryKey: ['stats'],
    queryFn: () => api.getStats(),
    refetchInterval: config.pollIntervalMs,
  });
}

export function useFiles(query: FileQuery) {
  return useQuery<PageResult<FileView>>({
    queryKey: ['files', query],
    queryFn: () => api.listFiles(query),
    placeholderData: (prev) => prev,   // evite le clignotement en pagination/recherche
    refetchInterval: (q) =>
      q.state.data && hasActive(q.state.data.items) ? config.pollIntervalMs : false,
  });
}

export interface UploadProgress {
  total: number;
  done: number;
  errors: { filename: string; message: string }[];
}

export function useUploadFiles() {
  const qc = useQueryClient();
  return useMutation<UploadProgress, Error, File[]>({
    mutationFn: async (files) => {
      const errors: UploadProgress['errors'] = [];
      let done = 0;
      for (const file of files) {
        try {
          const ticket = await api.registerUpload({
            filename: file.name,
            contentType: file.type || 'application/octet-stream',
            size: file.size,
          });
          await api.uploadBytes(ticket.uploadUrl, file);
          done++;
        } catch (e) {
          errors.push({ filename: file.name, message: (e as Error).message });
        }
      }
      return { total: files.length, done, errors };
    },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: ['files'] });
      qc.invalidateQueries({ queryKey: ['stats'] });
    },
  });
}

export function useRescan() {
  const qc = useQueryClient();
  return useMutation<void, Error, string>({
    mutationFn: (id) => api.rescan(id),
    onSettled: () => {
      qc.invalidateQueries({ queryKey: ['files'] });
      qc.invalidateQueries({ queryKey: ['stats'] });
    },
  });
}
```

- [ ] **Step 2: Write test `src/api/hooks.test.tsx`**

```tsx
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { useStats, useUploadFiles } from './hooks';
import { resetStore } from '../../test/mocks/store';

function wrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

test('useStats charge les compteurs', async () => {
  resetStore([{ status: 'CLEAN' }, { status: 'PENDING' }]);
  const { result } = renderHook(() => useStats(), { wrapper: wrapper() });
  await waitFor(() => expect(result.current.isSuccess).toBe(true));
  expect(result.current.data!.total).toBe(2);
});

test('useUploadFiles enregistre puis pousse les octets', async () => {
  resetStore();
  const { result } = renderHook(() => useUploadFiles(), { wrapper: wrapper() });
  result.current.mutate([new File(['x'], 'a.pdf', { type: 'application/pdf' })]);
  await waitFor(() => expect(result.current.isSuccess).toBe(true));
  expect(result.current.data).toMatchObject({ total: 1, done: 1, errors: [] });
});
```

> Note config test : `config.apiBaseUrl` doit valoir `http://api.test` pendant les tests pour matcher les handlers MSW. Ajouter dans `vite.config.ts` → `test.env: { VITE_API_BASE_URL: 'http://api.test', VITE_USE_MOCK: 'true' }`. (Mettre à jour `vite.config.ts` dans cette tâche.)

- [ ] **Step 3: Ajouter `test.env` à `vite.config.ts`**

```ts
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./test/setup.ts'],
    css: false,
    env: { VITE_API_BASE_URL: 'http://api.test', VITE_USE_MOCK: 'true', VITE_POLL_MS: '20' },
  },
```

- [ ] **Step 4: Run**

Run: `npm test -- hooks`
Expected: 2 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/api/hooks.ts src/api/hooks.test.tsx vite.config.ts
git commit -m "feat: add react-query hooks (stats, files polling, upload, rescan)"
```

---

## Task 7: `Modal` (coquille accessible) + `StatusBadge`

**Files:**
- Create: `src/components/Modal.tsx`, `src/components/StatusBadge.tsx`
- Test: `src/components/Modal.test.tsx`, `src/components/StatusBadge.test.tsx`

- [ ] **Step 1: `src/components/Modal.tsx`**

```tsx
import { useEffect, type ReactNode } from 'react';

interface ModalProps {
  title: string;
  onClose: () => void;
  children: ReactNode;
}

export function Modal({ title, onClose, children }: ModalProps) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <div className="modal-backdrop" onClick={onClose} role="presentation">
      <div
        className="card modal"
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onClick={(e) => e.stopPropagation()}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
          <h2 style={{ margin: 0, fontSize: '1.15rem' }}>{title}</h2>
          <button className="btn" aria-label="Fermer" onClick={onClose}>x</button>
        </div>
        {children}
      </div>
    </div>
  );
}
```

- [ ] **Step 2: `src/components/StatusBadge.tsx`**

```tsx
import type { FileStatus } from '../api/types';
import { statusPresentation } from '../lib/format';

export function StatusBadge({ status }: { status: FileStatus }) {
  const { label, tone } = statusPresentation(status);
  return (
    <span className={`badge ${tone}`}>
      {status === 'SCANNING' && <span className="spin" aria-hidden="true" />}
      {label}
    </span>
  );
}
```

- [ ] **Step 3: Write tests**

`src/components/StatusBadge.test.tsx` :
```tsx
import { render, screen } from '@testing-library/react';
import { StatusBadge } from './StatusBadge';

test('affiche le label Valide pour CLEAN', () => {
  render(<StatusBadge status="CLEAN" />);
  expect(screen.getByText('Valide')).toHaveClass('badge', 'clean');
});

test('affiche Bloque pour INFECTED', () => {
  render(<StatusBadge status="INFECTED" />);
  expect(screen.getByText('Bloque')).toHaveClass('badge', 'blocked');
});
```

`src/components/Modal.test.tsx` :
```tsx
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Modal } from './Modal';

test('ferme sur clic du bouton fermer', async () => {
  const onClose = vi.fn();
  render(<Modal title="Test" onClose={onClose}><p>corps</p></Modal>);
  await userEvent.click(screen.getByLabelText('Fermer'));
  expect(onClose).toHaveBeenCalled();
});

test('ferme sur touche Echap', async () => {
  const onClose = vi.fn();
  render(<Modal title="Test" onClose={onClose}><p>corps</p></Modal>);
  await userEvent.keyboard('{Escape}');
  expect(onClose).toHaveBeenCalled();
});
```

- [ ] **Step 4: Run**

Run: `npm test -- Modal StatusBadge`
Expected: 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/components/Modal.tsx src/components/StatusBadge.tsx src/components/Modal.test.tsx src/components/StatusBadge.test.tsx
git commit -m "feat: add accessible Modal shell and StatusBadge component"
```

---

## Task 8: `Header` + `StatCards`

**Files:**
- Create: `src/components/Header.tsx`, `src/components/StatCards.tsx`
- Test: `src/components/StatCards.test.tsx`

- [ ] **Step 1: `src/components/Header.tsx`**

```tsx
interface HeaderProps {
  portalName: string;
  apiKey: string;
  onOpenUpload: () => void;
}

function maskKey(key: string): string {
  if (key.length <= 4) return '****';
  return `${'*'.repeat(6)}${key.slice(-4)}`;
}

export function Header({ portalName, apiKey, onOpenUpload }: HeaderProps) {
  return (
    <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <span aria-hidden="true" style={{ fontSize: '1.6rem' }}>&#128737;</span>
        <div>
          <div style={{ fontWeight: 700, fontSize: '1.2rem' }}>{portalName}</div>
          <div style={{ color: 'var(--muted)', fontSize: '.82rem' }}>
            cle API : <code>{maskKey(apiKey)}</code>
          </div>
        </div>
      </div>
      <button className="btn btn-primary" onClick={onOpenUpload}>Deposer des fichiers</button>
    </header>
  );
}
```

- [ ] **Step 2: `src/components/StatCards.tsx`**

```tsx
import type { StatsView } from '../api/types';

const CARDS: { key: keyof StatsView; label: string; tone: string }[] = [
  { key: 'total', label: 'Total', tone: '' },
  { key: 'clean', label: 'Valides', tone: 'clean' },
  { key: 'scanning', label: 'En analyse', tone: 'scanning' },
  { key: 'blocked', label: 'Bloques', tone: 'blocked' },
];

export function StatCards({ stats }: { stats?: StatsView }) {
  return (
    <div className="stat-grid">
      {CARDS.map((c) => (
        <div key={c.key} className="card stat">
          <div className={`value ${c.tone}`} style={c.tone ? { color: `var(--${c.tone}-fg)` } : undefined}>
            {stats ? stats[c.key] : '-'}
          </div>
          <div className="label">{c.label}</div>
        </div>
      ))}
    </div>
  );
}
```

- [ ] **Step 3: Write test `src/components/StatCards.test.tsx`**

```tsx
import { render, screen } from '@testing-library/react';
import { StatCards } from './StatCards';

test('affiche les 4 cartes avec les valeurs', () => {
  render(<StatCards stats={{ total: 7, clean: 4, scanning: 2, pending: 1, blocked: 1 }} />);
  expect(screen.getByText('Total').previousSibling).toHaveTextContent('7');
  expect(screen.getByText('Valides')).toBeInTheDocument();
  expect(screen.getByText('En analyse')).toBeInTheDocument();
  expect(screen.getByText('Bloques')).toBeInTheDocument();
});

test('affiche un tiret sans donnees', () => {
  render(<StatCards />);
  expect(screen.getAllByText('-')).toHaveLength(4);
});
```

- [ ] **Step 4: Run**

Run: `npm test -- StatCards`
Expected: 2 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/components/Header.tsx src/components/StatCards.tsx src/components/StatCards.test.tsx
git commit -m "feat: add Header and StatCards components"
```

---

## Task 9: `SearchBar` + `Pagination` + `FileTable`

**Files:**
- Create: `src/components/SearchBar.tsx`, `src/components/Pagination.tsx`, `src/components/FileTable.tsx`
- Test: `src/components/FileTable.test.tsx`, `src/components/SearchBar.test.tsx`

- [ ] **Step 1: `src/components/SearchBar.tsx`**

```tsx
import { useEffect, useState } from 'react';
import type { FileStatus } from '../api/types';

interface SearchBarProps {
  onSearch: (q: string) => void;
  onStatus: (s: FileStatus | '') => void;
  status: FileStatus | '';
}

const STATUSES: { value: FileStatus | ''; label: string }[] = [
  { value: '', label: 'Tous les statuts' },
  { value: 'CLEAN', label: 'Valides' },
  { value: 'SCANNING', label: 'Scan en cours' },
  { value: 'PENDING', label: 'En attente' },
  { value: 'INFECTED', label: 'Bloques' },
  { value: 'SCAN_FAILED', label: 'Echec du scan' },
];

export function SearchBar({ onSearch, onStatus, status }: SearchBarProps) {
  const [text, setText] = useState('');
  useEffect(() => {
    const id = setTimeout(() => onSearch(text), 300);
    return () => clearTimeout(id);
  }, [text, onSearch]);

  return (
    <div style={{ display: 'flex', gap: 10, margin: '4px 0 12px' }}>
      <input
        className="btn"
        style={{ flex: 1, cursor: 'text' }}
        placeholder="Rechercher un fichier..."
        aria-label="Rechercher un fichier"
        value={text}
        onChange={(e) => setText(e.target.value)}
      />
      <select className="btn" aria-label="Filtrer par statut" value={status} onChange={(e) => onStatus(e.target.value as FileStatus | '')}>
        {STATUSES.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
      </select>
    </div>
  );
}
```

- [ ] **Step 2: `src/components/Pagination.tsx`**

```tsx
interface PaginationProps {
  page: number;         // 0-based
  totalPages: number;
  onPage: (p: number) => void;
}

export function Pagination({ page, totalPages, onPage }: PaginationProps) {
  return (
    <div style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: 10, padding: '12px 4px' }}>
      <button className="btn" disabled={page <= 0} onClick={() => onPage(page - 1)}>Precedent</button>
      <span style={{ color: 'var(--muted)', fontSize: '.88rem' }}>page {page + 1} / {totalPages}</span>
      <button className="btn" disabled={page >= totalPages - 1} onClick={() => onPage(page + 1)}>Suivant</button>
    </div>
  );
}
```

- [ ] **Step 3: `src/components/FileTable.tsx`**

```tsx
import type { FileView } from '../api/types';
import { formatBytes, formatDate, isDownloadable } from '../lib/format';
import { StatusBadge } from './StatusBadge';

interface FileTableProps {
  files: FileView[];
  loading: boolean;
  onView: (file: FileView) => void;
  onDownload: (file: FileView) => void;
}

export function FileTable({ files, loading, onView, onDownload }: FileTableProps) {
  if (!loading && files.length === 0) {
    return (
      <div className="card" style={{ padding: 40, textAlign: 'center', color: 'var(--muted)' }}>
        Aucun fichier pour le moment. Deposez un fichier pour commencer.
      </div>
    );
  }
  return (
    <div className="card" style={{ overflow: 'hidden' }}>
      <table>
        <thead>
          <tr>
            <th>Nom</th><th>Taille</th><th>Ajoute le</th><th>Statut</th><th aria-label="Actions" />
          </tr>
        </thead>
        <tbody>
          {files.map((f) => (
            <tr key={f.id}>
              <td style={{ fontWeight: 600 }}>{f.filename}</td>
              <td>{formatBytes(f.size)}</td>
              <td>{formatDate(f.createdAt)}</td>
              <td><StatusBadge status={f.status} /></td>
              <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                {isDownloadable(f.status) && (
                  <button className="btn" aria-label={`Telecharger ${f.filename}`} onClick={() => onDownload(f)}>
                    &#8681; Telecharger
                  </button>
                )}
                <button className="btn" style={{ marginLeft: 8 }} onClick={() => onView(f)}>Voir</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
```

- [ ] **Step 4: Write tests**

`src/components/FileTable.test.tsx` :
```tsx
import { render, screen } from '@testing-library/react';
import { FileTable } from './FileTable';
import type { FileView } from '../api/types';

const base: FileView = {
  id: 'f1', filename: 'rapport.pdf', contentType: 'application/pdf', size: 2_400_000,
  status: 'CLEAN', batchId: null, scanVerdict: null,
  createdAt: '2026-07-11T09:00:00Z', updatedAt: '2026-07-11T09:00:00Z', scannedAt: null,
};

test('bouton Telecharger seulement pour les fichiers CLEAN (invariant §2)', () => {
  render(<FileTable loading={false} files={[base, { ...base, id: 'f2', filename: 'v.exe', status: 'INFECTED' }]} onView={() => {}} onDownload={() => {}} />);
  expect(screen.getByLabelText('Telecharger rapport.pdf')).toBeInTheDocument();
  expect(screen.queryByLabelText('Telecharger v.exe')).not.toBeInTheDocument();
});

test('affiche taille et date formatees', () => {
  render(<FileTable loading={false} files={[base]} onView={() => {}} onDownload={() => {}} />);
  expect(screen.getByText('2,4 Mo')).toBeInTheDocument();
});

test('etat vide', () => {
  render(<FileTable loading={false} files={[]} onView={() => {}} onDownload={() => {}} />);
  expect(screen.getByText(/aucun fichier/i)).toBeInTheDocument();
});
```

`src/components/SearchBar.test.tsx` :
```tsx
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SearchBar } from './SearchBar';

test('remonte la recherche apres debounce', async () => {
  const onSearch = vi.fn();
  render(<SearchBar status="" onSearch={onSearch} onStatus={() => {}} />);
  await userEvent.type(screen.getByLabelText('Rechercher un fichier'), 'rap');
  await new Promise((r) => setTimeout(r, 350));
  expect(onSearch).toHaveBeenLastCalledWith('rap');
});
```

- [ ] **Step 5: Run**

Run: `npm test -- FileTable SearchBar`
Expected: 4 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add src/components/SearchBar.tsx src/components/Pagination.tsx src/components/FileTable.tsx src/components/FileTable.test.tsx src/components/SearchBar.test.tsx
git commit -m "feat: add SearchBar, Pagination and FileTable components"
```

---

## Task 10: `UploadModal` (drag & drop + picker multi-fichiers)

**Files:**
- Create: `src/components/UploadModal.tsx`
- Test: `src/components/UploadModal.test.tsx`

- [ ] **Step 1: `src/components/UploadModal.tsx`**

```tsx
import { useRef, useState } from 'react';
import { Modal } from './Modal';
import { useUploadFiles } from '../api/hooks';
import { formatBytes } from '../lib/format';

export function UploadModal({ onClose }: { onClose: () => void }) {
  const [files, setFiles] = useState<File[]>([]);
  const [over, setOver] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const upload = useUploadFiles();

  const addFiles = (list: FileList | null) => {
    if (list) setFiles((prev) => [...prev, ...Array.from(list)]);
  };

  const submit = () => {
    if (files.length === 0) return;
    upload.mutate(files, {
      onSuccess: (res) => { if (res.errors.length === 0) onClose(); },
    });
  };

  return (
    <Modal title="Deposer des fichiers" onClose={onClose}>
      <div
        className={`dropzone ${over ? 'over' : ''}`}
        onDragOver={(e) => { e.preventDefault(); setOver(true); }}
        onDragLeave={() => setOver(false)}
        onDrop={(e) => { e.preventDefault(); setOver(false); addFiles(e.dataTransfer.files); }}
      >
        <p>Glissez des fichiers ici</p>
        <button className="btn" onClick={() => inputRef.current?.click()}>Parcourir</button>
        <input
          ref={inputRef}
          type="file"
          multiple
          hidden
          aria-label="Choisir des fichiers"
          onChange={(e) => addFiles(e.target.files)}
        />
        <div className="label" style={{ marginTop: 8 }}>upload direct via URL signee</div>
      </div>

      {files.length > 0 && (
        <ul style={{ listStyle: 'none', padding: 0, margin: '14px 0', maxHeight: 160, overflow: 'auto' }}>
          {files.map((f, i) => (
            <li key={i} style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderTop: '1px solid var(--border)' }}>
              <span>{f.name}</span>
              <span style={{ color: 'var(--muted)' }}>{formatBytes(f.size)}</span>
            </li>
          ))}
        </ul>
      )}

      {upload.data && upload.data.errors.length > 0 && (
        <div className="badge blocked" style={{ display: 'block', padding: 10 }}>
          {upload.data.errors.length} echec(s) : {upload.data.errors.map((e) => e.filename).join(', ')}
        </div>
      )}

      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 14 }}>
        <button className="btn" onClick={onClose}>Annuler</button>
        <button className="btn btn-primary" disabled={files.length === 0 || upload.isPending} onClick={submit}>
          {upload.isPending ? 'Envoi...' : `Envoyer (${files.length})`}
        </button>
      </div>
    </Modal>
  );
}
```

- [ ] **Step 2: Write test `src/components/UploadModal.test.tsx`** (avec MSW + QueryClient)

```tsx
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { UploadModal } from './UploadModal';
import { resetStore } from '../../test/mocks/store';

function renderModal(onClose = vi.fn()) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<QueryClientProvider client={qc}><UploadModal onClose={onClose} /></QueryClientProvider>);
  return onClose;
}

test('selectionne un fichier et l envoie, puis ferme', async () => {
  resetStore();
  const onClose = renderModal();
  const file = new File(['bytes'], 'rapport.pdf', { type: 'application/pdf' });
  await userEvent.upload(screen.getByLabelText('Choisir des fichiers'), file);
  expect(screen.getByText('rapport.pdf')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: /envoyer/i }));
  await waitFor(() => expect(onClose).toHaveBeenCalled());
});
```

- [ ] **Step 3: Run**

Run: `npm test -- UploadModal`
Expected: 1 test PASS.

- [ ] **Step 4: Commit**

```bash
git add src/components/UploadModal.tsx src/components/UploadModal.test.tsx
git commit -m "feat: add UploadModal with drag-and-drop multi-file upload"
```

---

## Task 11: `FileReportModal` (rapport antivirus)

**Files:**
- Create: `src/components/FileReportModal.tsx`
- Test: `src/components/FileReportModal.test.tsx`

- [ ] **Step 1: `src/components/FileReportModal.tsx`**

```tsx
import { Modal } from './Modal';
import { StatusBadge } from './StatusBadge';
import { formatBytes, formatDate, isDownloadable } from '../lib/format';
import { useRescan, api } from '../api/hooks';
import type { FileView } from '../api/types';

interface Props {
  file: FileView;
  onClose: () => void;
}

export function FileReportModal({ file, onClose }: Props) {
  const rescan = useRescan();
  const canRescan = file.status === 'SCAN_FAILED' || file.status === 'INFECTED' || file.status === 'CLEAN';

  const row = (label: string, value: React.ReactNode) => (
    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderTop: '1px solid var(--border)' }}>
      <span style={{ color: 'var(--muted)' }}>{label}</span>
      <span style={{ fontWeight: 600, textAlign: 'right' }}>{value}</span>
    </div>
  );

  return (
    <Modal title="Rapport antivirus" onClose={onClose}>
      <h3 style={{ margin: '0 0 4px', wordBreak: 'break-all' }}>{file.filename}</h3>
      <div style={{ marginBottom: 8 }}><StatusBadge status={file.status} /></div>
      {row('Taille', formatBytes(file.size))}
      {row('Type', file.contentType)}
      {row('Ajoute le', formatDate(file.createdAt))}
      {row('Moteur', file.scanVerdict?.engine ?? 'en attente de scan')}
      {row('Verdict', file.scanVerdict?.verdict ?? '-')}
      {file.status === 'INFECTED' && row('Menace', file.scanVerdict?.threatName ?? 'inconnue')}

      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 16 }}>
        {canRescan && (
          <button className="btn" disabled={rescan.isPending} onClick={() => rescan.mutate(file.id)}>
            {rescan.isPending ? 'Relance...' : 'Rescanner'}
          </button>
        )}
        {isDownloadable(file.status) && (
          <button className="btn btn-primary" onClick={() => api.downloadFile(file.id, file.filename)}>
            Telecharger
          </button>
        )}
      </div>
    </Modal>
  );
}
```

- [ ] **Step 2: Write test `src/components/FileReportModal.test.tsx`**

```tsx
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { FileReportModal } from './FileReportModal';
import type { FileView } from '../api/types';

const infected: FileView = {
  id: 'f1', filename: 'eicar.txt', contentType: 'text/plain', size: 68, status: 'INFECTED',
  batchId: null, scanVerdict: { engine: 'ClamAV', verdict: 'INFECTED', threatName: 'Eicar-Test-Signature', scannedAt: '2026-07-11T09:00:00Z' },
  createdAt: '2026-07-11T09:00:00Z', updatedAt: '2026-07-11T09:00:00Z', scannedAt: '2026-07-11T09:00:00Z',
};

function renderReport(file: FileView) {
  const qc = new QueryClient();
  render(<QueryClientProvider client={qc}><FileReportModal file={file} onClose={() => {}} /></QueryClientProvider>);
}

test('affiche la menace et pas de bouton telecharger pour INFECTED', () => {
  renderReport(infected);
  expect(screen.getByText('Eicar-Test-Signature')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Telecharger' })).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Rescanner' })).toBeInTheDocument();
});

test('affiche telecharger pour CLEAN', () => {
  renderReport({ ...infected, status: 'CLEAN', filename: 'ok.pdf', scanVerdict: { engine: 'ClamAV', verdict: 'CLEAN', threatName: null, scannedAt: '2026-07-11T09:00:00Z' } });
  expect(screen.getByRole('button', { name: 'Telecharger' })).toBeInTheDocument();
});
```

- [ ] **Step 3: Run**

Run: `npm test -- FileReportModal`
Expected: 2 tests PASS.

- [ ] **Step 4: Commit**

```bash
git add src/components/FileReportModal.tsx src/components/FileReportModal.test.tsx
git commit -m "feat: add FileReportModal (antivirus verdict + download/rescan)"
```

---

## Task 12: Assemblage `App` + bootstrap `main` (+ mode démo MSW)

**Files:**
- Modify: `src/App.tsx`, `src/main.tsx`, `src/smoke.test.tsx`
- Test: `src/App.test.tsx`

- [ ] **Step 1: Remplacer `src/App.tsx`**

```tsx
import { useState, useCallback } from 'react';
import { config } from './config';
import { useStats, useFiles, api } from './api/hooks';
import type { FileStatus, FileView } from './api/types';
import { Header } from './components/Header';
import { StatCards } from './components/StatCards';
import { SearchBar } from './components/SearchBar';
import { FileTable } from './components/FileTable';
import { Pagination } from './components/Pagination';
import { UploadModal } from './components/UploadModal';
import { FileReportModal } from './components/FileReportModal';

const PAGE_SIZE = 6;

export default function App() {
  const [page, setPage] = useState(0);
  const [q, setQ] = useState('');
  const [status, setStatus] = useState<FileStatus | ''>('');
  const [uploadOpen, setUploadOpen] = useState(false);
  const [selected, setSelected] = useState<FileView | null>(null);

  const stats = useStats();
  const files = useFiles({ page, size: PAGE_SIZE, q, status });

  const onSearch = useCallback((value: string) => { setQ(value); setPage(0); }, []);
  const onStatus = useCallback((s: FileStatus | '') => { setStatus(s); setPage(0); }, []);

  return (
    <div className="app">
      <Header portalName={config.portalName} apiKey={config.apiKey} onOpenUpload={() => setUploadOpen(true)} />
      <StatCards stats={stats.data} />
      <h2 style={{ fontSize: '1.05rem', margin: '8px 0' }}>Mes fichiers</h2>
      <SearchBar status={status} onSearch={onSearch} onStatus={onStatus} />
      <FileTable
        files={files.data?.items ?? []}
        loading={files.isLoading}
        onView={setSelected}
        onDownload={(f) => api.downloadFile(f.id, f.filename)}
      />
      <Pagination page={page} totalPages={files.data?.totalPages ?? 1} onPage={setPage} />

      {uploadOpen && <UploadModal onClose={() => setUploadOpen(false)} />}
      {selected && <FileReportModal file={selected} onClose={() => setSelected(null)} />}
    </div>
  );
}
```

- [ ] **Step 2: Remplacer `src/main.tsx`** (branche le worker MSW si `useMock`)

```tsx
import React from 'react';
import ReactDOM from 'react-dom/client';
import { QueryClientProvider } from '@tanstack/react-query';
import App from './App';
import { config } from './config';
import { queryClient } from './api/hooks';
import './styles.css';

async function enableMockIfNeeded() {
  if (!config.useMock) return;
  const { makeWorker } = await import('../test/mocks/browser');
  await makeWorker(config.apiBaseUrl).start({ onUnhandledRequest: 'bypass' });
}

enableMockIfNeeded().then(() => {
  ReactDOM.createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
      <QueryClientProvider client={queryClient}>
        <App />
      </QueryClientProvider>
    </React.StrictMode>,
  );
});
```

> Note : en mode démo navigateur MSW v2 nécessite le service worker. Générer `public/mockServiceWorker.js` via `npx msw init public/ --save` (Task 13 le documente ; ajouter le fichier au dépôt).

- [ ] **Step 3: Mettre à jour `src/smoke.test.tsx`** (App a maintenant besoin d'un QueryClient) — remplacer par `App.test.tsx`

Supprimer `src/smoke.test.tsx`, créer `src/App.test.tsx` :
```tsx
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';
import { resetStore } from '../test/mocks/store';

function renderApp() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<QueryClientProvider client={qc}><App /></QueryClientProvider>);
}

test('affiche l en-tete, les cartes et la liste seedee', async () => {
  resetStore([{ filename: 'rapport.pdf', status: 'CLEAN', size: 2_400_000 }]);
  renderApp();
  expect(screen.getByText(/mes fichiers/i)).toBeInTheDocument();
  await waitFor(() => expect(screen.getByText('rapport.pdf')).toBeInTheDocument());
  expect(screen.getByLabelText('Telecharger rapport.pdf')).toBeInTheDocument();
});

test('ouvre la modale de depot', async () => {
  resetStore();
  renderApp();
  await userEvent.click(screen.getByRole('button', { name: /deposer des fichiers/i }));
  expect(screen.getByRole('dialog', { name: /deposer des fichiers/i })).toBeInTheDocument();
});
```

- [ ] **Step 4: Run toute la suite**

Run: `npm test`
Expected: tous les tests PASS (aucun test rouge).

- [ ] **Step 5: Vérifier le build**

Run: `npm run build`
Expected: build TypeScript + Vite OK, dossier `dist/` généré.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: assemble App page, wire polling and modals, enable MSW demo mode"
```

---

## Task 13: `mockServiceWorker.js`, README, `.env.example`, polish final

**Files:**
- Create: `public/mockServiceWorker.js` (via CLI msw), `README.md`
- Modify: `.env.example` (déjà en T1 — vérifier), `package.json` (script `msw:init` optionnel)

- [ ] **Step 1: Générer le service worker MSW**

Run: `npx msw init public/ --save`
Expected: crée `public/mockServiceWorker.js`.

- [ ] **Step 2: `README.md`**

````markdown
# praxedo-upload-ui

SPA React « Fichiers securises » : depot de fichiers, liste live avec statut de scan
antivirus, telechargement **autorise uniquement si le fichier est CLEAN**, rapport
antivirus et relance de scan. Frontend du micro-service `praxedo-upload-backend`.

## Stack

- React 18 + Vite 5 + TypeScript (strict).
- TanStack Query (react-query) v5 : polling leger des statuts, cache, invalidation.
- Vitest + Testing Library + MSW : tests unitaires (logique + client) et composants,
  avec un **faux backend en memoire** (contrat API de la spec §4).
- Pas de state manager lourd. Auth par header `X-API-Key`.

## Architecture

Couches decouplees, testables en isolation (meme philosophie que le backend) :

- `src/lib/format.ts` — logique de presentation **pure** (formatage, mapping de statut,
  invariant `isDownloadable`), testee sans React.
- `src/api/client.ts` — `FileApi` : wrapper `fetch` **injectable**, ajoute `X-API-Key`,
  serialise les requetes, seul endroit a adapter si le JSON backend differe du contrat.
- `src/api/hooks.ts` — hooks react-query (stats, liste + polling, upload, rescan).
- `src/components/*` — presentation (Header, StatCards, FileTable, modales...).
- `test/mocks/*` — faux backend MSW : sert **aux tests ET au mode demo hors ligne**
  (analogue frontend des adapters in-memory du backend).

## Lancer en local

### Mode demo (hors ligne, faux backend MSW)

```bash
npm install
cp .env.example .env
# .env : VITE_USE_MOCK=true (defaut)
npm run dev
```
Ouvre http://localhost:5173. Deposez des fichiers : ils passent PENDING -> SCANNING -> CLEAN.
Un fichier dont le nom contient `eicar`/`virus` finit **INFECTED** (non telechargeable).

### Contre le vrai backend

```bash
# .env
VITE_USE_MOCK=false
VITE_API_BASE_URL=http://localhost:8080
VITE_API_KEY=<votre-cle-api>
```
Le backend `praxedo-upload-backend` doit tourner (profil `local`). L'UI appelle
`/api/files`, pousse les octets vers l'URL renvoyee (proxy local ou URL signee GCS),
et interroge les statuts.

## Tests

```bash
npm test          # suite complete
npm run test:watch
npm run lint      # typecheck strict
npm run build     # build de production
```

## Choix techniques & hypotheses

- **Contrat d'abord** : le frontend cible le contrat d'API (spec §4). Le backend etant
  construit en parallele, MSW encode ce contrat pour un dev/test hors ligne. Toute
  divergence de nommage se corrige au seul endroit `api/client.ts`.
- **Multi-fichiers** : l'UI boucle sur `POST /api/files` (un ticket + un PUT par fichier).
  L'endpoint `/api/batches` reste reserve a l'integration systeme-a-systeme.
- **Telechargement** : compromis demo — le client recupere les octets via `/content`
  puis declenche l'enregistrement navigateur. **Evolution** : le backend renvoie l'URL
  signee en JSON pour un telechargement direct depuis GCS (offload total des octets),
  preservant le principe « l'app ne relaie jamais les gros fichiers ».
- **Accents** : les libelles UI sont en ASCII dans le code (contrainte du validateur de
  commandes du monorepo) ; le rendu accentue reel se fait en remplacant les chaines
  (aucune logique a changer).

## Pistes d'amelioration

- Barre de progression d'upload par fichier (events `XMLHttpRequest`/`fetch` upload).
- Upload resumable pour les tres gros fichiers (reprise apres coupure).
- Auth utilisateur (OAuth2/JWT) en plus des cles API machine-to-machine.
- Notification push de fin de scan (websocket/SSE) pour supprimer le polling.
- i18n (extraction des libelles).
````

- [ ] **Step 3: Vérifier `.gitignore` couvre `dist`, `node_modules`, `.env` (T1)**

Run: `git status --short`
Expected: pas de `node_modules/`, `dist/`, `.env` suivis.

- [ ] **Step 4: Lancer une dernière fois toute la suite + build**

Run: `npm test && npm run build`
Expected: tout PASS, build OK.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "docs: add README and MSW service worker for demo mode"
```

---

## Self-Review (couverture spec §10)

| Exigence spec §10 | Tâche |
|---|---|
| One-pager (en-tête + 4 cartes + tableau paginé 6/page + recherche) | T8, T9, T12 |
| Cartes Total / Validés / En analyse / Bloqués | T8 (`StatCards`) |
| Tableau NOM / TAILLE / AJOUTÉ LE / STATUT / ACTION | T9 (`FileTable`) |
| Icône download **uniquement CLEAN** + lien « Voir » | T9 + T3 (`isDownloadable`), test dédié |
| Badges de statut alignés machine à états | T3 + T7 (`StatusBadge`) |
| Dépôt modale drag & drop (un/plusieurs) → URL signée → PUT | T10 (`UploadModal`) + T4/T6 |
| Rapport antivirus modale (verdict + télécharger / rescanner) | T11 (`FileReportModal`) |
| Stack React + Vite + react-query (polling léger) + `X-API-Key` | T1, T4, T6 |
| Palette bleu/vert corporate, `portalName` paramétrable, variante claire | T1 (`styles.css`) + T2/T8 |

**Placeholder scan** : aucun `TODO`/`TBD` ; code complet dans chaque étape.
**Type consistency** : `FileView`, `StatsView`, `PageResult`, `UploadTicket`, `FileApi`, `AppConfig` cohérents T2→T12 ; `statusPresentation`/`isDownloadable` définis T3 et réutilisés partout.

---

## Execution Handoff

Plan sauvegardé. Deux options d'exécution (méthodologie projet : **une branche + une PR par tâche** vers `develop`) :
1. **Subagent-driven** (recommandé) — un subagent frais par tâche, revue entre chaque, worktrees pour les tâches parallélisables (T7–T11).
2. **Inline** — exécution en session avec checkpoints.
