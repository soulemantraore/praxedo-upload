# praxedo-upload-backend

Micro-service de gestion de **fichiers sécurisés** : `upload → scan antivirus → téléchargement contrôlé`.

> **Invariant de sécurité** : un fichier n'est **jamais** servi tant qu'il n'a pas été validé (`CLEAN`) par l'antivirus.

- **Stack** : Java 21 · Spring Boot 3.3 · Maven.
- **Architecture** : hexagonale (domaine POJO + ports + adapters), cible de déploiement **GCP**.
- Conception détaillée : `docs/superpowers/specs/` et `docs/superpowers/plans/` (dépôt de specs du projet).

## Architecture

Trois couches, dépendances dirigées vers le domaine (Dependency Inversion) :

```
web (contrôleurs REST, filtre clé API)      ← infrastructure
persistence / storage / scanner / queue     ← infrastructure (adapters des ports)
application (cas d'usage : upload, scan, query, download, reconcile, apiKey)
domain (FileRecord + machine à états, ports/interfaces, value objects)   ← ne dépend de RIEN
```

Le domaine ne connaît ni Spring, ni GCS, ni ClamAV : il ne dépend que de **ports** (`FileStorage`, `FileMetadataRepository`, `AntivirusScanner`, `ScanQueue`, `ApiClientRepository`, `IdGenerator`). Les adapters sont choisis par **profil Spring**.

### Profils
- **`local` / `test`** : adapters in-memory (repos), filesystem (`LocalFileStorage` + proxy HTTP simulant GCS), `FakeAntivirusScanner` (détecte la signature de test **EICAR**), scan en process. Tourne **sans aucune dépendance GCP**.
- **`gcp`** : adapters réels, **profil démarrable de bout en bout** : `GcsFileStorage` (URLs signées V4), `JpaFileMetadataRepository`/`JpaApiClientRepository` (Cloud SQL/Postgres + Flyway), `ClamavScanner` (clamd), `PubSubScanQueue` + endpoint push `/internal/scan-events`. Requiert : credentials GCP (`GOOGLE_APPLICATION_CREDENTIALS`), un bucket, un topic, une base Postgres.

### Flux réel (profil gcp)
```
Client → GCS (upload direct, URL signée)
GCS "object finalize" → Pub/Sub → push /internal/scan-events → scan-worker
   scan-worker lit GCS → clamd (sidecar, localhost) → écrit le statut (Cloud SQL)
Client → GET /content : URL signée de download uniquement si CLEAN
```
`clamd` n'est jamais dans notre code : c'est l'image officielle **co-localisée en sidecar** ; le worker l'appelle via le port `AntivirusScanner`.

### Infra réelle en local (optionnel)
`docker-compose.yml` lance **ClamAV + émulateur Pub/Sub + PostgreSQL + émulateur GCS** pour expérimenter le flux réel :
```bash
docker compose up -d
JAVA_HOME=<jdk-21> mvn spring-boot:run -Dspring-boot.run.profiles=gcp
```
(Les tests d'intégration démarrent leurs propres conteneurs via Testcontainers — pas besoin de ce compose pour `mvn test`. Un vrai GCS/Pub/Sub nécessite `GOOGLE_APPLICATION_CREDENTIALS`.)

### Cycle de vie d'un fichier
```
PENDING → SCANNING → CLEAN      (seul état téléchargeable)
                   → INFECTED   (octets supprimés, download → 403)
                   → SCAN_FAILED (échec technique, retries bornés)
PENDING → EXPIRED  (upload jamais reçu, réconciliation par TTL)
```

## API (toutes les routes `/api/**` : header `X-API-Key`, scopées à l'owner)

| Méthode | Route | Rôle |
|---|---|---|
| `POST` | `/api/files` | Enregistre un upload → `201 { id, status: PENDING, uploadUrl }` |
| `POST` | `/api/batches` | Enregistre un lot (systèmes tiers) → `batchId` + items |
| `GET` | `/api/batches/{batchId}` | Statuts d'un lot + résumé |
| `GET` | `/api/files` | Liste paginée (`?q`, `?status`, `?page`, `?size`) |
| `GET` | `/api/files/stats` | Compteurs par statut |
| `GET` | `/api/files/{id}` | Métadonnées + verdict |
| `GET` | `/api/files/{id}/content` | `302` vers l'URL de download **si `CLEAN`**, sinon `403` |
| `POST` | `/api/files/{id}/rescan` | (Re)lance un scan |

## Lancer en local

Prérequis : **JDK 21** et Maven (le `JAVA_HOME` doit pointer sur un JDK 21).

```bash
JAVA_HOME=<jdk-21> mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Au démarrage, une **clé API de démo** est créée et loguée :
```
Cle API de demo (profil local) : pk_XXXX…
```

### Démo `curl`
```bash
KEY=pk_XXXX…   # récupérée dans les logs

# 1) Enregistrer un upload
curl -s -X POST localhost:8080/api/files -H "X-API-Key: $KEY" \
     -H "Content-Type: application/json" \
     -d '{"filename":"rapport.pdf","contentType":"application/pdf","size":1024}'
# → { "id":"…", "status":"PENDING", "uploadUrl":"http://localhost:8080/api/_local/upload?key=…" }

# 2) Envoyer les octets (en prod : PUT direct sur l'URL signée GCS)
curl -s -X PUT "<uploadUrl>" --data-binary @rapport.pdf

# 3) Déclencher le scan
#    (en prod : automatique via l'événement GCS "object finalize" → Pub/Sub ;
#     en local, pas d'événement GCS → on le déclenche via /rescan)
curl -s -X POST "localhost:8080/api/files/<id>/rescan" -H "X-API-Key: $KEY"

# 4) Vérifier le statut, puis télécharger si CLEAN
curl -s "localhost:8080/api/files/<id>" -H "X-API-Key: $KEY"
curl -sL "localhost:8080/api/files/<id>/content" -H "X-API-Key: $KEY" -o rapport-dl.pdf
```

Pour tester le **blocage d'un fichier infecté**, uploader un fichier contenant la chaîne de test EICAR : le statut passe à `INFECTED` et le download renvoie `403`.

## Build & tests
```bash
JAVA_HOME=<jdk-21> mvn test
```
**Pyramide** : majorité de tests unitaires POJO rapides (domaine + application, sans contexte Spring), + tests d'intégration (`@SpringBootTest` + MockMvc) pour les adapters web et le flux e2e (chemin infecté bloqué, isolation entre owners).

## Choix techniques & hypothèses (résumé — détail dans la spec)
- **Flux asynchrone** : l'upload répond immédiatement (`PENDING`), le scan tourne en arrière-plan → tient la charge et les fichiers de tailles très variables.
- **URLs signées direct-to-GCS** (en prod) : les octets ne transitent jamais par l'app ; le port `FileStorage` cache ce détail (adapter local = proxy HTTP).
- **Antivirus derrière un port** : ClamAV par défaut en prod, `FakeAntivirusScanner` (EICAR) en local/test ; un adapter SaaS serait trivial à brancher.
- **Clés API par-client** (hachées SHA-256) + **scoping par owner** : chaque client ne voit que ses fichiers. Hypothèse : machine-to-machine ; pour des utilisateurs humains → OAuth2/JWT (évolution).
- **Injection de dépendances stricte** (`Clock`, `IdGenerator` injectés) → tout est testable sans Spring.

## Pistes d'amélioration
- Adapters GCP (GCS signed URLs, Cloud SQL/JPA, Pub/Sub, ClamAV sidecar) — jalons suivants.
- Auth OAuth2/JWT pour utilisateurs connectés ; endpoint admin de gestion des clés.
- Exécuteur dédié (`ThreadPoolTaskExecutor`) pour le scan async local ; monitoring de la DLQ en prod.
- Chiffrement au repos (CMEK), quotas/rate-limiting par client, webhooks de fin de scan (éviter le polling).

## Modèle de branches
`main` (défaut, stable) · `develop` (intégration). Une **branche + une PR par tâche** vers `develop`.
