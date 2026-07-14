# Backend Jalon 3 — Adapters GCP (GCS + Cloud SQL) — Plan

> Même méthodo : une branche + une PR par tâche vers `develop`, TDD, séquentiel (pom partagé). Spec : D7, D8. Testcontainers (Docker requis).

## Objectif
Rendre le profil **`gcp`** entièrement démarrable de bout en bout : brancher les derniers adapters réels.
- **`JpaFileMetadataRepository`** + **`JpaApiClientRepository`** (Cloud SQL / PostgreSQL, Spring Data JPA + Flyway).
- **`GcsFileStorage`** (URLs signées V4 via IAM signBlob ; read/delete/exists via le SDK GCS).
- **Déclenchement auto** du scan par la notification GCS « object finalize » → Pub/Sub → endpoint push (reporté du jalon 2).
- Câblage complet + config + docker-compose + README.

Le domaine et l'application **ne changent pas** (sauf ajout d'une méthode de port). On ne branche que des adapters.

## Décisions (recommandations)
1. **Persistance** : le domaine `FileRecord`/`ApiClient` reste **POJO** ; la persistance a son **propre modèle** `@Entity` (`FileEntity`, `ApiClientEntity`) mappé vers/depuis le domaine dans l'adapter ([[bonnes-pratiques]] : domaine sans annotation framework). **Flyway** pour le schéma (pas de `ddl-auto`). Test : **Testcontainers Postgres** (haute valeur — nos mappings/requêtes sont du vrai code).
2. **`GcsFileStorage`** : `read`/`delete`/`exists` testés contre **`fake-gcs-server`** (Testcontainers). Les **URLs signées** (upload/download) sont de la fine glu SDK (comme Pub/Sub) → test unitaire léger / validées au déploiement, pas contre l'émulateur (le signing V4 sur fake-gcs est peu fiable).
3. **Auto-trigger GCS** : la notification GCS met le **nom d'objet** (= `storageKey`) dans le message ; l'endpoint push résout `storageKey → FileRecord → scan`. Double chemin dans `ScanEventsController` : message `data=fileId` (nos rescans) OU notification GCS (attribut `objectId`). Ajoute `findByStorageKey` au port `FileMetadataRepository` (+ impls in-memory & JPA).
4. **Profil gcp complet** : avec JPA + GCS + ClamAV + Pub/Sub actifs, le profil devient démarrable. On teste chaque adapter isolément (Postgres IT, fake-gcs IT) ; le bout-en-bout gcp complet est validé au déploiement (infra), pas dans un `@SpringBootTest` gcp (trop de dépendances externes).

## Dépendances build (pom, ajoutées au fil des tâches)
- J3.1 : `spring-boot-starter-data-jpa`, `org.postgresql:postgresql`, `flyway-core` + `flyway-database-postgresql` ; test `org.testcontainers:postgresql`.
- J3.2 : `com.google.cloud:google-cloud-storage` (BOM déjà présent depuis J2.2) ; test `org.testcontainers:gcloud` (fake-gcs) — à valider (arch).

## Tâches (DAG — séquentiel)

| # | Tâche | Branche | Test | Docker |
|---|-------|---------|------|--------|
| J3.1 | JPA : `FileEntity`/`ApiClientEntity` + `JpaFileMetadataRepository`/`JpaApiClientRepository` (`@Profile gcp`) + Flyway `V1__schema.sql` + `findByStorageKey` (port + in-memory) | `task/j3-1-jpa` | Testcontainers Postgres (save/find/search/count/stale/batch/apiclient) | **oui** |
| J3.2 | `GcsFileStorage` (`@Profile gcp`) : signed URLs V4 + read/delete/exists | `task/j3-2-gcs` | fake-gcs (read/delete/exists) + unit signed-URL | **oui** |
| J3.3 | Auto-trigger : `ScanEventsController` gère la notification GCS (objet → storageKey → fileId) | `task/j3-3-gcs-trigger` | MockMvc payload notification GCS canned | non |
| J3.4 | Câblage profil gcp + `application-gcp.yml` (datasource, bucket) + docker-compose (+ Postgres, fake-gcs) + README | `task/j3-4-wiring` | build vert | non |

## Hors périmètre (jalon infra)
- Déploiement Cloud Run (api + worker + ClamAV sidecar + Cloud SQL + GCS + Pub/Sub) → **infra** (gcloud + Makefile + CI/CD).
- Sécurisation OIDC réelle de l'endpoint push.

## Suivi des PR (jalon 3)
- **PR #22** — J3.1 JPA/Postgres + Flyway (`@Profile gcp`) — 🟨 à fusionner (55 tests). Auto-config base/JPA/Flyway exclue par défaut, réactivée en gcp → local/test restent sans base.
- **PR #23** — J3.2 `GcsFileStorage` (URLs signées V4 + read/delete/exists ; mock + IT fake-gcs) — 🟨 à fusionner (60 tests).
- **PR #24** — J3.3 auto-trigger GCS (notification object-finalize → scan) — 🟨 à fusionner (63 tests). Flux gcp désormais bout-en-bout automatique.
- **PR #25** — J3.4 câblage complet profil gcp (test de wiring + docker-compose + README) — 🟨 à fusionner (64 tests). **Dernière du jalon 3.**

## Jalon 3 — bouclé (reste #25 à fusionner)
Les 4 ports ont leur adapter GCP réel (GCS, Cloud SQL/JPA, ClamAV, Pub/Sub), câblés dans un profil `gcp` **démarrable de bout en bout** (vérifié par `GcpProfileWiringTest`). 4 PR (#22-#25). Auto-trigger GCS complet.
**→ Backend techniquement déployable sur GCP.** Reste : l'**infra** (gcloud + Makefile + CI/CD + Cloud Run), puis le **frontend**.
