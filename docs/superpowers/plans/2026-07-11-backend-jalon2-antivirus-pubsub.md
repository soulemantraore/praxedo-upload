# Backend Jalon 2 — ClamAV réel + Pub/Sub — Plan

> Exécution : même méthodo que le jalon 1 (une branche + une PR par tâche vers `develop`, TDD, worktrees). Spec de référence : `docs/superpowers/specs/2026-07-10-...-design.md` (D2, D6, D8).

## Objectif
Remplacer les doublures de scan par de la **vraie infra**, testée pour de vrai via **Testcontainers** :
- `ClamavScanner` — adapter réel du port `AntivirusScanner` (protocole clamd INSTREAM).
- `PubSubScanQueue` — adapter réel du port `ScanQueue` (publie sur un topic Pub/Sub).
- `ScanEventsController` — endpoint **push** `/internal/scan-events` qui reçoit les messages Pub/Sub et déclenche le scan.

Le domaine et l'application **ne changent pas** (on ne touche qu'aux adapters + config) — c'est tout l'intérêt des ports.

## Décisions de périmètre (à valider)

1. **Client clamd = protocole brut** (socket TCP, commande `zINSTREAM`, chunks longueur-préfixée) — ~40 lignes, **zéro dépendance**. Pas de lib tierce. *(Alternative : lib `clamav-client`, écartée pour éviter une dépendance sur un protocole simple.)*
2. **Client Pub/Sub = `google-cloud-pubsub`** (client officiel) via le BOM `com.google.cloud:libraries-bom`. Adapter mince ; pas de `spring-cloud-gcp` (trop lourd pour notre besoin).
3. **Modèle de déclenchement** : en jalon 2, le scan reste déclenché par **`ScanQueue.enqueue(fileId)` → Pub/Sub → push → `FileScanService.scan`**. Le déclenchement **automatique** depuis l'événement GCS « object finalize » (qui donne un nom d'objet, pas un fileId) est **reporté au jalon 3** (il nécessite GCS + le mapping storageKey→fileId).
4. **Profils** : `ClamavScanner` et `PubSubScanQueue` annotés `@Profile("gcp")` (dormants pour l'instant). Le **câblage complet du profil `gcp`** (avec GcsFileStorage + JPA) est fait au **jalon 3**. En jalon 2, chaque adapter est testé **en isolation** via Testcontainers (instancié directement, sans contexte Spring complet).
5. **Sécurité du push** : `/internal/**` en `permitAll` dans `SecurityConfig`. En prod, l'endpoint push est sécurisé par le **jeton OIDC** de la push subscription Pub/Sub (documenté, non implémenté ici).

## Dépendances build (pom)
- `com.google.cloud:libraries-bom` (BOM, `import`), `com.google.cloud:google-cloud-pubsub`.
- Test : `org.testcontainers:junit-jupiter`, `org.testcontainers:testcontainers` (GenericContainer pour l'image `clamav/clamav`), `org.testcontainers:gcloud` (`PubSubEmulatorContainer`). Version via `org.testcontainers:testcontainers-bom`.

## Tâches (DAG)

| # | Tâche | Branche | Bloquée par | Test | Docker ? |
|---|-------|---------|-------------|------|----------|
| J2.1 | `ClamdClient` (protocole brut) + `ClamavScanner` (`@Profile gcp`) | `task/j2-1-clamav` | — | Testcontainers `clamav/clamav` : bytes sains → CLEAN, EICAR → INFECTED | **oui** |
| J2.2 | `PubSubScanQueue` (publish) + config topic | `task/j2-2-pubsub-queue` | — | Testcontainers `PubSubEmulatorContainer` : enqueue → message reçu sur une subscription | **oui** |
| J2.3 | `ScanEventsController` push `/internal/scan-events` | `task/j2-3-push-endpoint` | — | MockMvc : payload push Pub/Sub canned (base64 `{fileId}`) → `FileScanService.scan` appelé | non |
| J2.4 | `docker-compose.yml` (ClamAV + émulateur) + `application-gcp.yml` + README (flux gcp) | `task/j2-4-compose-config` | J2.1,J2.2,J2.3 | build vert | non |

J2.1, J2.2, J2.3 sont **indépendants** (adapters/fichiers distincts) → parallélisables. J2.4 vient après (assemble config + doc).

## Notes de test
- Les tests Testcontainers portent `@Testcontainers` + `@EnabledIfDockerAvailable`-style (ou `assumeTrue`) pour ne pas casser un build sans Docker — mais ici **Docker sera actif** (choix utilisateur), donc ils tournent réellement.
- `ScanEventsController` : le message push Pub/Sub a la forme `{ "message": { "data": "<base64>", "messageId": "...", ... }, "subscription": "..." }`. On décode `data` (base64) → JSON `{ "fileId": "..." }` → `scan(fileId)`. Répondre 204 (ack) même si le fichier est inconnu (évite les redéliveries infinies).

## Hors périmètre (jalons suivants)
- GcsFileStorage (URLs signées V4), JPA/Cloud SQL, notification GCS→Pub/Sub, câblage complet du profil `gcp` → **jalon 3**.
- Déploiement Cloud Run (api + worker + ClamAV sidecar) → **infra**.

## Suivi des PR (jalon 2)
- **PR #18** — J2.1 `ClamavScanner` (clamd réel + Testcontainers `clamav/clamav-debian` arm64) — ✅ fusionnée (46 tests). Image Debian retenue (l'Alpine est amd64 seulement).
- **PR #19** — J2.2 `PubSubScanQueue` (`google-cloud-pubsub`, `@Profile gcp`) — 🟨 à fusionner (47 tests). Test **unitaire mock** (on teste notre mapping fileId→message, pas le client Google) — l'émulateur validerait le code tiers ; le vrai test à valeur était ClamAV (protocole écrit à la main).
- **PR #20** — J2.3 `ScanEventsController` (endpoint push `/internal/scan-events`, ack idempotent) — 🟨 à fusionner (49 tests).
- **PR #21** — J2.4 docker-compose + application-gcp.yml + README — 🟨 à fusionner (49 tests, config/doc). **Dernière du jalon 2.**

## Jalon 2 — bouclé (reste #21 à fusionner)
Antivirus **ClamAV réel** (protocole clamd testé contre un vrai conteneur Testcontainers) + **transport Pub/Sub** + **endpoint push**, derrière les ports (profil `gcp`). 4 PR (#18-#21). Le domaine/application n'ont pas bougé.
**Suite : jalon 3** (`GcsFileStorage` URLs signées V4 + JPA/Cloud SQL + câblage complet du profil `gcp`), puis frontend, puis infra.
- **Décision D6 confirmée : A (sidecar)**, B (scanner autonome) évaluée puis écartée pour la simplicité.
