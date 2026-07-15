# Document de conception — praxedo-upload

Micro-service de **gestion de fichiers sécurisés** : recevoir des fichiers (utilisateurs et
systèmes tiers), les faire scanner par un antivirus, et ne servir que ceux qui sont sains.

> **Invariant** : un fichier n'est **jamais** servi tant qu'il n'a pas été déclaré `CLEAN` par
> l'antivirus. Tout le reste des choix découle de cette garantie.

Ce document résume les **choix techniques et architecturaux**, les **hypothèses** et les
**pistes d'amélioration**. Schéma du flux : voir [« Le flux en bref »](#le-flux-en-bref) plus bas.

---

## 1. Choix techniques et architecturaux

**Stack (imposée)** : Java 21 · Spring Boot 3.3 (backend) — React 18 · Vite · TypeScript (UI) —
Python · FastAPI · ClamAV (scanner). **Déploiement** : GCP Cloud Run.

- **Architecture hexagonale (ports & adapters)** — un **domaine pur** (POJO, sans framework)
  entouré de **ports** (interfaces) ; les **adapters** concrets sont choisis par le **profil
  Spring** (`local`/`test` sans dépendance externe, `gcp` en production). On teste le métier sans
  Spring ni base, et on remplace un adapter (stockage, scanner, file) sans toucher au domaine.
- **Trois services déployés séparément** — API (`backend`), analyse (`scanner`, dépôt séparé),
  portail (`ui`). Ils s'échelonnent indépendamment ; une analyse lourde ne pénalise jamais une
  requête de service.
- **Invariant fail-safe** — le téléchargement n'est autorisé que si le statut est `CLEAN` ; tout
  le reste (`PENDING`, `SCANNING`, `INFECTED`, `SCAN_FAILED`) → **403**. En cas de doute, on ne sert pas.
- **Octets hors de l'application** — dépôt et téléchargement se font **directement** sur GCS via
  des **URLs signées V4** ; l'app ne relaie jamais les octets. Elle ne gère que des métadonnées.
- **Analyse asynchrone** — le port `ScanQueue` découple dépôt et analyse. En production :
  notification GCS `OBJECT_FINALIZE` → **Pub/Sub** (+ DLQ, retries) → **worker**.
- **Scanner externalisé, appelé en OIDC** — le worker appelle le service antivirus en HTTP (jeton
  OIDC) avec l'URI GCS ; le scanner lit GCS, scanne via **ClamAV** et renvoie le verdict. Le
  scanner **ne touche jamais la base** : seul le backend écrit le verdict (pas de faux « CLEAN »
  usurpable).
- **Sécurité machine-à-machine** — authentification par clé API (`X-API-Key`) ; seul le **hash
  SHA-256** est stocké, **isolation par owner** (chaque client ne voit que ses fichiers).
- **Persistance** — domaine POJO sans annotation JPA ; entités JPA dédiées + **Flyway** ;
  **PostgreSQL** managé (Supabase) pour les métadonnées et statuts.

## 2. Hypothèses

- **Clients = systèmes tiers** (usage machine-à-machine) → l'authentification par clé API suffit ;
  pas d'authentification humaine à ce stade.
- **ClamAV suffit** comme moteur antivirus pour le périmètre demandé ; il reste remplaçable
  derrière le port `AntivirusScanner` (autre moteur, SaaS…).
- **Fichiers de tailles très variables et nombreux clients simultanés** → jamais de relais des
  octets par l'app (URLs signées) et analyse asynchrone.
- **Un scan peut échouer ou se perdre** → statut `SCAN_FAILED` (retries bornés) et
  **réconciliation par TTL** des uploads jamais suivis d'octets (`EXPIRED`).
- **Base PostgreSQL managée externe** (Supabase) acceptable pour les métadonnées/statuts ; les
  octets restent sur GCS.
- **Cloud Run** convient : `worker` scale-to-zero (min-instances = 0), `scanner` privé et « chaud »
  (min-instances = 1, base de signatures en mémoire).

## 3. Pistes d'amélioration

- **Authentification humaine** OAuth2/JWT en complément des clés API ; endpoint admin de
  gestion/rotation des clés.
- **Push de fin de scan** (webhook / SSE) pour supprimer le polling côté UI.
- **Téléchargement direct** depuis GCS via URL signée renvoyée au navigateur (décharge complètement
  le backend des gros fichiers en download).
- **Durcissement** : vérification applicative du jeton OIDC Pub/Sub, chiffrement au repos (CMEK),
  quotas / rate-limiting par client.
- **Analyse renforcée** : plusieurs moteurs antivirus, inspection de contenu / DLP, limites de
  taille et de type.
- **Observabilité** : métriques et traces (temps de scan, taux d'infection), alerting sur
  `SCAN_FAILED` et sur la DLQ.
- **UI** : traiter les vulnérabilités `npm audit` avant mise en production.

---

## Le flux en bref

Schéma interactif (Excalidraw) : **<https://excalidraw.com/#json=WlmXLQeMkIVZmWFvqoLRp,eroBDBj5VzStQo7ZRB078w>**
· source versionnée : [`diagrams/flux.excalidraw`](diagrams/flux.excalidraw) (ouvrir dans Excalidraw → *Open*)

```
   Utilisateur ──1. requête──▶ Frontend UI (proxy) ──2. X-API-Key──▶ Backend API
        │                                                                 │
        │ 3. PUT direct (URL signée)                                      │ lit le statut
        ▼                                                                 ▼
      GCS ──finalize──▶ Pub/Sub ──push OIDC──▶ Worker ──POST /scan──▶ Scanner + ClamAV
                                                  │  ◀──── verdict ────────┘
                                                  ▼
                                          Postgres (Supabase)
                                          ├─ CLEAN     ▶ download (302)
                                          └─ INFECTED  ▶ 403 (quarantaine)
```

1. **Enregistrement** — `POST /api/files` : crée un `FileRecord` (`PENDING`) et renvoie un ticket
   (URL signée, TTL court).
2. **Dépôt direct** — le client `PUT` les octets vers GCS (l'app ne les voit pas).
3. **Déclenchement** — GCS `OBJECT_FINALIZE` → Pub/Sub → `/internal/scan-events` (worker).
4. **Analyse** — le worker appelle le scanner (OIDC) ; ClamAV rend un verdict ; le worker écrit
   `CLEAN` ou `INFECTED` en base.
5. **Service** — `GET /api/files/{id}/content` : **302** vers une URL signée **si `CLEAN`**, sinon **403**.

> Détail complet (couches, profils, topologie GCP, API) :
> [`../praxedo-upload-backend/docs/architecture.html`](../praxedo-upload-backend/docs/architecture.html).
