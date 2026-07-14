# Micro-service de fichiers sécurisés — Document de conception

**Date :** 2026-07-10
**Statut :** validé (brainstorming)

## 1. Contexte & objectif

Concevoir et développer un micro-service de gestion de fichiers sécurisés capable de :

1. Recevoir et conserver des fichiers transmis par des utilisateurs ou des systèmes tiers.
2. **Garantir qu'aucun fichier n'est servi sans avoir été préalablement scanné par un antivirus.**
3. Permettre le téléchargement des fichiers validés via une interface programmatique (API).

Le service doit supporter de nombreux utilisateurs simultanés et manipuler des fichiers de tailles très variables.

**Stack imposée :** Java / Spring Boot (backend) · React (frontend) · déploiement sur **GCP**.
**Principes :** SOLID, sans sur-ingénierie (YAGNI).

## 2. Invariant central

> Un fichier n'est téléchargeable **que si son statut est `CLEAN`**.

Tout le reste de l'architecture découle de cet invariant. Il est appliqué à un seul endroit (le point d'émission de l'URL de téléchargement) et repose sur la machine à états du §5.

## 3. Vue d'ensemble de l'architecture

Topologie **découplée** sur GCP (le scan ne bloque jamais l'API, l'app ne relaie jamais les gros octets) :

```
React (praxedo-upload-ui)
   │  1. POST /api/files (métadonnées)              ┌─────────────────┐
   ├───────────────────────────────────────────────►   file-api      │  Cloud Run
   │  ← 201 { id, status: PENDING, uploadUrl }      │  (Spring Boot)  │
   │                                                └───────┬─────────┘
   │  2. PUT octets (URL signée)                            │ ligne PENDING
   ├──────────────────────────────►  GCS  ◄────────────────┘ (Cloud SQL / Postgres)
   │                                  │
   │                                  │ 3. object finalize
   │                                  ▼
   │                               Pub/Sub  ──►  scan-worker  ──► ClamAV (sidecar, clamd)
   │                                              (Cloud Run)      4. scan
   │                                                 │ 5. statut CLEAN / INFECTED / SCAN_FAILED
   │  6. GET /api/files/{id}/content                 ▼
   └───────────────────────────────────────►  file-api  ── si CLEAN ──► URL signée download → GCS
                                                       └─ sinon ────────► 403
```

**Composants GCP :**

| Composant | Rôle | Service GCP |
|---|---|---|
| `file-api` | REST : upload (URL signée), liste, stats, détail, download gated | Cloud Run |
| `scan-worker` | Consomme Pub/Sub, lit l'objet depuis GCS, appelle ClamAV, met à jour le statut | Cloud Run (endpoint push Pub/Sub) |
| ClamAV | Moteur antivirus (`clamd`) | Conteneur **sidecar** du `scan-worker` (localhost:3310) |
| Stockage octets | Fichiers | Cloud Storage (GCS) |
| Métadonnées & statut | Base relationnelle | Cloud SQL (PostgreSQL) |
| File de scan | Découplage upload/scan, retries, DLQ | Pub/Sub (+ notification GCS `object finalize`) |
| Secrets | Clé API, identifiants DB | Secret Manager |

`file-api` et `scan-worker` sont **la même application Spring Boot** déployée en deux services Cloud Run avec des profils différents (`api` / `worker`) — même image, pas de duplication de code.

## 4. API REST

Toutes les routes sont sous `/api`, protégées par une **clé API par-client** (header `X-API-Key`). La clé identifie un **owner** (`ApiClient`) ; **toutes les routes sont automatiquement scopées à l'owner** — un client ne voit/télécharge que ses propres fichiers (sinon `404`).

| Méthode | Route | Rôle | Réponse |
|---|---|---|---|
| `POST` | `/api/files` | Enregistre un upload unitaire (`filename`, `contentType`, `size`) | `201 { id, status: PENDING, uploadUrl, uploadExpiresAt }` |
| `POST` | `/api/batches` | Enregistre un **lot** de fichiers (`{ files: [...] }`) | `201 { batchId, items: [{ id, filename, status, uploadUrl, uploadExpiresAt }] }` |
| `GET` | `/api/batches/{batchId}` | Statuts de tous les fichiers d'un lot + résumé | `200 { batchId, items[], summary: { pending, scanning, clean, infected, failed } }` |
| `GET` | `/api/files` | Liste paginée + recherche (`?page`, `?size`, `?q`, `?status`, `?batchId`) | `200 { items[], page, totalPages, totalElements }` |
| `GET` | `/api/files/stats` | Compteurs par statut (cartes de métriques) | `200 { total, clean, scanning, pending, blocked }` |
| `GET` | `/api/files/{id}` | Métadonnées + verdict antivirus | `200 { id, filename, size, status, scanVerdict, ... }` |
| `GET` | `/api/files/{id}/content` | Téléchargement **gated** | `302` → URL signée si `CLEAN`, sinon `403` |
| `POST` | `/api/files/{id}/rescan` | Relance un scan (SCAN_FAILED ou manuel) | `202` |
| `POST` | `/internal/scan-events` | Endpoint **push Pub/Sub** du `scan-worker` (non exposé publiquement) | `204` |

**Flux d'upload en 2 temps** : `POST /api/files` (ou `/api/batches` pour plusieurs) crée la/les ligne(s) `PENDING` et renvoie une **URL d'upload signée** par fichier ; le client fait un `PUT` direct des octets vers GCS. La fin d'upload (événement GCS `object finalize`) déclenche le scan via Pub/Sub. Aucun appel « confirm » n'est nécessaire ; les lignes `PENDING` sans upload sont nettoyées par réconciliation (§5).

**Intégration système tiers (batch)** : un système tiers utilise sa clé API dédiée pour `POST /api/batches` (plusieurs fichiers d'un coup), pousse les octets, puis interroge `GET /api/batches/{batchId}` pour récupérer les statuts de toute la liste. Chaque fichier est scanné indépendamment.

## 5. Cycle de vie & machine à états

```
[POST /api/files] → PENDING ──(object finalize → Pub/Sub)──► SCANNING ──► CLEAN
                       │                                          │
                       │ (pas d'upload avant TTL)                 ├──► INFECTED   (octets supprimés/quarantaine)
                       ▼                                          │
                    EXPIRED                                       └──► SCAN_FAILED (retries bornés → DLQ)
```

| Statut | Signification | Téléchargeable |
|---|---|---|
| `PENDING` | Ligne créée, en attente d'upload / de scan | non |
| `SCANNING` | Worker en cours de scan | non |
| `CLEAN` | Scan OK | **oui** |
| `INFECTED` | Menace détectée ; octets supprimés/quarantaine, métadonnée + audit conservés | non (403) |
| `SCAN_FAILED` | Erreur technique (scanner indisponible, timeout) ; retries bornés avec backoff, puis DLQ | non |
| `EXPIRED` | URL d'upload jamais utilisée (orphelin) | non |

- **Retries** : gérés par Pub/Sub (redélivraison automatique) + dead-letter topic après N tentatives. Un `SCAN_FAILED` ≠ une menace.
- **Réconciliation** : un job planifié re-file les lignes bloquées (`SCANNING` trop anciennes) et marque `EXPIRED` les `PENDING` sans objet GCS au-delà d'un TTL.

### 5.1 Modèle de données

**`api_client`** — un système tiers / utilisateur d'API.

| Colonne | Type | Note |
|---|---|---|
| `id` | UUID | owner id |
| `name` | text | libellé du client |
| `api_key_hash` | text | **hash** de la clé (jamais en clair) |
| `active` | bool | révocation |
| `created_at` | timestamptz | |

**`file`** — un fichier et son statut.

| Colonne | Type | Note |
|---|---|---|
| `id` | UUID | |
| `owner_id` | UUID → `api_client.id` | **scoping** : toutes les requêtes filtrent dessus |
| `batch_id` | UUID, nullable | regroupement de lot (pas d'agrégat séparé) |
| `filename`, `content_type`, `size_bytes` | | métadonnées |
| `storage_key` | text | clé de l'objet GCS |
| `status` | enum | PENDING/SCANNING/CLEAN/INFECTED/SCAN_FAILED/EXPIRED |
| `scan_engine`, `scan_verdict`, `threat_name` | | verdict antivirus (nom de menace si INFECTED) |
| `scan_attempts` | int | pour les retries bornés |
| `created_at`, `updated_at`, `scanned_at` | timestamptz | |

Un « batch » n'est **pas** une entité lourde : c'est un `batch_id` partagé par plusieurs lignes `file`. `GET /api/batches/{batchId}` = filtre `owner_id + batch_id`.

## 6. Abstractions & SOLID (Dependency Inversion)

Architecture en couches (hexagonale allégée) : le **domaine** ne dépend d'aucune techno d'infrastructure, seulement de **ports (interfaces)**. Les adapters concrets sont injectés par Spring selon le profil.

| Port (domaine) | Rôle | Adapter par défaut | Adapters alternatifs |
|---|---|---|---|
| `FileStorage` | `createUploadUrl` / `createDownloadUrl` / `read` / `delete` | `GcsFileStorage` (GCS) | `LocalFileStorage` (filesystem, dev/tests — retombe sur des endpoints proxy) |
| `FileMetadataRepository` | Persistance des métadonnées & statut | Adapter **JPA / PostgreSQL** | tout moteur SQL via **changement de l'URL JDBC** ; autre paradigme = nouvel adapter |
| `AntivirusScanner` | `scan(...) → ScanVerdict` | `ClamavScanner` (protocole `clamd` INSTREAM) | adapter SaaS (Cloudmersive/MetaDefender) trivial à brancher |
| `ScanQueue` / `EventPublisher` | Publier une demande de scan | Adapter **Pub/Sub** | in-process (dev/tests) |

**« Changer juste l'URL de la base »** : en restant dans la famille SQL, Spring Data JPA + l'URL JDBC (`spring.datasource.url`, + dialecte) suffisent. Le port `FileMetadataRepository` permet en plus de changer de paradigme (Firestore, Mongo) via un nouvel adapter.

**Choix du provider par configuration** (profils / properties Spring), jamais en dur dans le domaine. Un seul adapter réel par port + un adapter dev = pas de sur-ingénierie.

### 6.1 Testabilité & injection de dépendances

Principe : la logique métier ne dépend que de **ports injectés** → testable en isolation, framework à la périphérie. On **ne dépend pas d'un contexte Spring** pour tester la logique.

- **Injection par constructeur partout** (jamais de `@Autowired` sur champ) → toute classe instanciable en test sans Spring.
- **Domaine POJO** : entités & value objects sans annotations framework (`@Entity`/`@Component`). La persistance a son **propre modèle JPA** mappé vers/depuis le domaine.
- **Pyramide de tests** : majorité de tests unitaires POJO rapides (domaine + application, sans Spring) ; tests d'intégration (`@SpringBootTest` + Testcontainers) réservés aux **adapters**.
- **Doubles de test = adapters in-memory** : `LocalFileStorage`, repo in-memory, `ScanQueue` in-process, fake scanner — servent au dev **et** aux tests (pas de mocking massif).
- **Injecter `Clock`** (java.time.Clock) et la **génération d'ID** → TTL, retries, timestamps déterministes.
- **Config typée** (`@ConfigurationProperties`) plutôt que des `@Value` éparpillés.
- **Exceptions du domaine** mappées vers HTTP dans un `@ControllerAdvice` ; **transactions à la frontière applicative** ; **value objects immuables** ; pas d'état statique caché.

### Découpage des modules

- `domain` — entités (`FileRecord`), value objects (`FileStatus`, `ScanVerdict`), ports (interfaces), règles/invariants.
- `application` — cas d'usage : `RegisterUpload`, `HandleScanEvent`, `ScanFile`, `RequestDownload`, `ListFiles`, `GetStats`, `Reconcile`.
- `infrastructure` — adapters : `web` (contrôleurs REST + filtre clé API), `persistence` (JPA), `storage` (GCS/Local), `antivirus` (ClamAV), `messaging` (Pub/Sub).
- `config` — configuration Spring, profils `local` / `gcp` / `api` / `worker`.

## 7. Gestion des gros fichiers

Les octets transitent **directement entre le client et GCS** via des **URLs signées** de courte durée ; l'app ne relaie jamais les gros fichiers (bande passante offloadée hors de Cloud Run). L'URL de téléchargement n'est émise **que si le statut est `CLEAN`**, préservant l'invariant §2. Le port `FileStorage` masque ce détail au client (il suit toujours « une URL »).

**Mécanisme (URL signée V4)** : le backend génère une URL GCS portant une **signature** qui encode le bucket + chemin exact de l'objet, la méthode autorisée (`PUT`), une expiration (≈15 min), et des contraintes (content-type, plage de taille via `x-goog-content-length-range`). C'est **GCS** qui valide la signature au `PUT` du client — l'API n'est jamais dans le flux des octets. Sur Cloud Run, le SDK signe via l'API IAM **`signBlob`** du compte de service attaché → **aucun fichier de clé privée à gérer**. Pour les très gros fichiers, même mécanique en **upload resumable** (par morceaux, reprise après coupure).

## 8. Sécurité

- **Authentification par clé API par-client** : header `X-API-Key`, vérifiée par un filtre Spring Security. Chaque clé est **générée pour un système tiers / utilisateur d'API** et identifie un **owner** (`ApiClient`). Les clés sont stockées **hachées** en base (`api_client.api_key_hash`), jamais en clair.
- **Scoping par propriétaire** : chaque fichier appartient à son owner ; **toutes les routes filtrent sur `owner_id`** → un client ne peut ni lister, ni consulter, ni télécharger les fichiers d'un autre (`404`). Appliqué de façon transverse (résolution de l'owner dans le filtre, propagé au contexte applicatif).
- **Gestion des clés (démo)** : seed via config / cible Makefile (`make create-api-key NAME=...`) ; un endpoint admin protégé par une clé maître est documenté comme évolution.
- **Évolution documentée** : pour des **utilisateurs humains connectés**, ajouter **OAuth2 / JWT** (Spring Security resource server) — coexiste avec les clés API machine-to-machine.
- **Confidentialité** : les fichiers ne quittent jamais l'infrastructure (ClamAV auto-hébergé, pas d'antivirus tiers).
- **Fichiers infectés** : octets supprimés/quarantaine dès détection ; jamais servables.

## 9. Antivirus (ClamAV)

- ClamAV tourne en **conteneur sidecar** du `scan-worker` (Cloud Run multi-container) ; le worker parle à `clamd` sur `localhost:3310` (protocole INSTREAM) — c'est « l'antivirus disponible via une API ».
- L'adapter `ClamavScanner` implémente le port `AntivirusScanner` : le worker lit l'objet depuis GCS (`FileStorage.read`) et le streame à `clamd`.
- **Hypothèse / tradeoff** : ClamAV charge ~1–2 Go de base de signatures → `min-instances = 1` et mémoire suffisante sur le service worker (évite les cold starts et le rechargement).

## 10. Frontend (`praxedo-upload-ui`)

Suit la **maquette Cloud Design** fournie (voir §12 « Hypothèses » pour la source exacte). Palette bleu/vert corporate, portail brandé (`portalName` paramétrable), variante claire par défaut.

- **One-pager** : en-tête + 4 cartes de métriques (Total / Validés / En analyse / Bloqués) + tableau paginé (6/page) + recherche.
- **Tableau** : NOM / TAILLE / AJOUTÉ LE / STATUT / ACTION ; icône de téléchargement **affichée uniquement pour les fichiers `CLEAN`** ; lien « Voir ».
- **Badges de statut** alignés sur la machine à états (Validé / Scan en cours + progression / En attente / Bloqué).
- **Dépôt** : modale drag & drop (un ou plusieurs fichiers) → demande une URL signée puis `PUT` vers GCS.
- **Rapport antivirus** : modale au clic sur un fichier (verdict + télécharger / rescanner).
- **Stack** : React + Vite + react-query (polling léger pour le statut) ; pas de state manager lourd ; appels API avec `X-API-Key`.

## 11. Déploiement, CI/CD, tests, observabilité

### Structure des dépôts (polyrepo)
- **`praxedo-upload-backend`** — Spring Boot ; `.gitignore` propre.
- **`praxedo-upload-ui`** — React ; `.gitignore` propre.

### Infrastructure (pas de Terraform)
- Provisioning et déploiement via **commandes `gcloud`**.
- **`Makefile`** (côté infra) avec cibles pour déployer manuellement, ex. : `make infra-up` (buckets, Cloud SQL, Pub/Sub, topics/DLQ, notification GCS), `make deploy-api`, `make deploy-worker`, `make deploy-ui`.
- Secrets via **Secret Manager**.

### CI/CD
- **GitHub Actions** par dépôt : build + tests, puis déploiement via **`gcloud`** (`gcloud run deploy`, etc.). Deux pipelines indépendants (backend / frontend).

### Tests
- **Pyramide** (voir §6.1) : majorité de tests unitaires POJO rapides (domaine + application, **sans contexte Spring**), grâce à l'injection par constructeur et aux ports.
- **Domaine** : tests unitaires (règles, machine à états, invariant, `Clock` injecté).
- **Intégration** : **JUnit 5 + Testcontainers** (PostgreSQL, ClamAV) pour les adapters et le flux réel ; `LocalFileStorage` + `ScanQueue` in-process pour les tests sans GCP.

### Observabilité
- **Logs structurés** → Cloud Logging ; statut & audit persistés en base (traçabilité des verdicts).

### Configuration
- Profils Spring : `local` (Docker Compose : Postgres + ClamAV + émulateur GCS/MinIO) et `gcp`. Un Docker Compose permet de tout faire tourner en local.

## 12. Hypothèses

- « Antivirus disponible via une API » ⇒ interprété comme ClamAV auto-hébergé exposant son API `clamd` (INSTREAM), abstrait derrière `AntivirusScanner` ; un adapter SaaS resterait trivial.
- Un « utilisateur » (système tiers) est identifié par sa **clé API par-client** (machine-to-machine) ; le **multi-tenant léger** (owner + scoping des fichiers) est **dans le périmètre**. La gestion humaine des accès fins (rôles) reste une évolution (OAuth/JWT).
- La maquette Cloud Design fait foi pour l'UI ; sa source exacte (`Fichiers sécurisés.dc.html`) sera exportée depuis le design tool au moment de l'implémentation pour une reproduction fidèle (captures de référence prises pendant le brainstorming).
- Tailles de fichiers jusqu'à plusieurs Go ⇒ justifie les URLs signées direct-to-GCS.

## 13. Pistes d'amélioration (pour le README)

- **Auth** : passage à OAuth2/JWT + gestion des accès (propriété des fichiers, rôles).
- **Antivirus** : ajout d'un adapter SaaS ou multi-moteurs ; mise à jour programmée des signatures.
- **Scalabilité** : autoscaling fin, quotas par client, rate limiting.
- **Résilience** : monitoring/alerting sur la DLQ, tableau de bord des SCAN_FAILED.
- **Sécurité** : chiffrement au repos par clé client (CMEK), analyse de contenu supplémentaire (type MIME réel, taille max par plan).
- **Reprise** : webhooks de notification de fin de scan pour éviter le polling côté client.

## 14. Récapitulatif des décisions

| # | Décision |
|---|---|
| D1 | Système complet déployé sur GCP (end-to-end) |
| D2 | Antivirus derrière un port `AntivirusScanner`, ClamAV par défaut |
| D3 | Polyrepo : `praxedo-upload-backend` + `praxedo-upload-ui` |
| D4 | Flux de scan **asynchrone** |
| D5 | Machine à états `PENDING→SCANNING→CLEAN/INFECTED/SCAN_FAILED` (+ EXPIRED) |
| D6 | Topologie **découplée via Pub/Sub** |
| D7 | Ports `FileStorage` + `FileMetadataRepository` (défaut GCS + JPA/Postgres) |
| D8 | Gros fichiers : **URLs signées direct-to-GCS** |
| D9 | Auth : **clés API par-client** + propriété/scoping des fichiers (OAuth/JWT en évolution) |
| D10 | Frontend : suit la **maquette Cloud Design** (palette Praxedo) |
| D11 | IaC : **`gcloud` + Makefile**, pas de Terraform |
| D12 | Testabilité : injection par constructeur, domaine POJO, tests sans Spring (voir §6.1) |
| D13 | **Opérations par lot (batch)** pour l'intégration système tiers |
