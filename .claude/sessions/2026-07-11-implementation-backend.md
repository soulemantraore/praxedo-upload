# Session 2026-07-11 — Implémentation backend (jalon 1)

## Objet
Démarrage de l'implémentation du backend `praxedo-upload-backend` selon le plan `docs/superpowers/plans/2026-07-10-backend-foundation.md` et son ordonnancement `…-EXECUTION.md`.

## Mise en place
- Dépôt cloné : `git@github.com:soulemantraore/praxedo-upload-backend.git` (l'ancien nom `…-api` est abandonné).
- Branches : **`main`** (défaut, commit initial README + `.gitignore`) + **`develop`** (intégration) par-dessus.
- Toolchain : **Java 21** installé via Homebrew. **Non-défaut** (la machine a Java 17) → toujours préfixer `mvn` par le `JAVA_HOME` openjdk@21 (voir `CLAUDE.md` → Commandes).
- Aucun code backend préexistant à supprimer (disque déjà propre) ; docs + mémoire intacts.

## Méthodologie (exigence utilisateur)
- **Une branche + une PR par tâche** vers `develop` ; l'utilisateur relit et fusionne ; puis tâche suivante.
- Tâches **indépendantes** d'une vague → **en parallèle** (git worktrees isolés) ; tâches **dépendantes** → **bloquées** (voir DAG dans `…-EXECUTION.md`).
- Vagues 5 et 6 réalisées via **subagents parallèles** (un par worktree) pour préserver le contexte.

## Réalisations (PR)
- **PR #1** — Task 01 Scaffold (+ config transverse : `Clock`, `@ConfigurationPropertiesScan`, `@EnableAsync/@EnableScheduling`). ✅ fusionnée.
- **PR #2** — Task 02 `FileStatus` (+ refactor `Map.ofEntries`). ✅ fusionnée.
- **PR #3** — Task 03 `ScanVerdict`. ✅ fusionnée.
- **PR #4** — Task 04 `FileRecord` (+ **Lombok `@Builder`** + accesseurs `@Getter`/`@Accessors(fluent)`). ✅ fusionnée.
- **PR #5** — Task 05 value objects + `ApiClient` + exceptions. ✅ fusionnée.
- **PR #6** — Task 06 les 6 ports. ✅ fusionnée.
- **PR #7/#8/#9** — Task 07 repos in-memory + `UuidIdGenerator` / Task 08 `LocalFileStorage` + `StorageProperties` / Task 09 `FakeAntivirusScanner` (EICAR). ✅ fusionnées (subagents parallèles).
- **PR #10 (fix)** — `@ActiveProfiles("test")` sur `contextLoads`. ✅ fusionnée. **Détecté par la Vague 6** : les 4 subagents ont vu que le smoke test (`@SpringBootTest` sans profil) ne pouvait plus câbler le 1er `@Service` (adapters `@Profile(local,test)`) ; 3 se sont arrêtés (BLOCKED), Task 12 s'était débloquée seule via un `application.yml` de test (normalisé/retiré ensuite).
- **PR #11/#12/#13/#14** — Vague 6 : Task 10-13 (services applicatifs). ✅ fusionnées.
- **PR #15** — Task 14 sécurité (filtre clé API + SecurityConfig). ✅ fusionnée.
- **PR #16** — Task 15 contrôleurs REST + proxy local + handler (+ DTOs regroupés dans `UploadRequests`). ✅ fusionnée.
- **PR #17** — Task 16 e2e (infecté bloqué via EICAR, isolation owner) + README backend. 🟨 à fusionner (44 tests). **Dernière tâche du jalon 1.**

### Jalon 1 — bouclé (reste #17 à fusionner)
Backend testable **entièrement en local sans GCP** : domaine + application + web + sécurité + adapters in-memory/local/fake. 16 tâches (une PR chacune) + 1 fix (#10). 44 tests verts.

### Décisions/pièges notables de la fin de jalon
- Fix #10 : `@ActiveProfiles("test")` (adapters profil-gated + smoke test sans profil).
- Task 15 : correctif d'un artefact MockMvc (double-encodage `%2F` d'une URL template → passer la clé décodée via `.param`). Consigné dans CLAUDE.md → Pièges connus.
- DTOs : regroupés dans un conteneur au nom explicite (`UploadRequests`), pas un fichier par record. [[bonnes-pratiques]] point 12.

## Jalon 2 backend (2026-07-12) — ClamAV réel + Pub/Sub — bouclé
Plan : `docs/superpowers/plans/2026-07-11-backend-jalon2-antivirus-pubsub.md`. Décision **D6 = A (sidecar)** confirmée (B, scanner autonome via callback, évaluée puis écartée pour simplicité).
- **PR #18** J2.1 `ClamavScanner` : protocole clamd INSTREAM écrit à la main (zéro dépendance) + Testcontainers **vrai ClamAV** (`clamav/clamav-debian`, arm64 — l'image Alpine est amd64 seulement). ✅
- **PR #19** J2.2 `PubSubScanQueue` : `google-cloud-pubsub`, test unitaire **mock** (on teste notre mapping, pas le client Google — distinction assumée vs ClamAV où le risque était dans NOTRE protocole). ✅
- **PR #20** J2.3 `ScanEventsController` : endpoint push `/internal/scan-events`, ack idempotent. ✅
- **PR #21** J2.4 docker-compose + application-gcp.yml + README. 🟨 à fusionner.
Environnement : Docker démarré (Testcontainers). Profil `gcp` dormant (câblage complet au jalon 3).

## Jalon 3 backend (2026-07-13) — Adapters GCP — bouclé
Plan : `docs/superpowers/plans/2026-07-12-backend-jalon3-gcp-adapters.md`.
- **PR #22** J3.1 JPA/Postgres + Flyway. Subtilité : auto-config base/JPA/Flyway exclue par défaut (application.yml), réactivée en gcp → local/test sans base. Package jpa réorganisé en `entities`/`repositories`/`adapters` (exigence utilisateur, [[bonnes-pratiques]] pt 13). Test Testcontainers Postgres. ✅
- **PR #23** J3.2 `GcsFileStorage` (URLs signées V4 + read/delete/exists) : mock (signed URLs) + IT fake-gcs. ✅
- **PR #24** J3.3 auto-trigger : notification GCS object-finalize → `findByStorageKey` → scan (double chemin dans `ScanEventsController`). ✅
- **PR #25** J3.4 câblage complet profil gcp : `GcpProfileWiringTest` (4 adapters réels, Storage/Publisher mockés, Postgres réel) + docker-compose complet + README. 🟨 à fusionner.
→ **Backend techniquement déployable sur GCP.** Reste : infra (gcloud/Makefile/CI-CD/Cloud Run) + frontend.
Constante conservée `OBJECT_FINALIZE` (valeur imposée par GCS).

### Leçon (process subagents parallèles)
Un défaut d'infrastructure partagé exposé par plusieurs tâches parallèles → le régler en **une PR de fix dédiée fusionnée d'abord**, puis rebaser les branches dessus (éviter que chaque branche duplique le fix → conflits). Les subagents ont eu le bon réflexe de ne pas committer un build rouge.

## Décisions prises pendant l'implémentation (détail dans decisions-archi.md)
- **D14 — Lombok** : `@Builder` sur entités (encapsulé par fabriques), accesseurs `@Getter`+`@Accessors(fluent=true)` ; `record` pour les VO simples.
- **Refactor T2** : `Map.ofEntries` plutôt que `Map.of` pour lisibilité des valeurs composées.
- **Ajustement plan** : config transverse (Clock/annotations) avancée dans Task 01 pour garder chaque état fusionné vert. Queues de scan séparées par profil (`SynchronousScanQueue`=test, `InProcessScanQueue`=local).

## Prochaines étapes
- Ouvrir/fusionner les PR de la Vague 6 (T10-T13).
- **Vague 7** : Task 14 sécurité (filtre clé API + `SecurityConfig`) — bloquée par T13.
- **Vague 8** : Task 15 contrôleurs REST + proxy local — bloquée par T10/T11/T12/T14.
- **Vague 9** : Task 16 test e2e + README backend — bloquée par T15.
- Puis jalons 2 (ClamAV/Pub/Sub), 3 (GCS/Cloud SQL), frontend, infra.

## À retenir
- Le validateur de commandes bloque : caractères accentués, séquence `===`, chemins `/usr` ou `/bin/` → garder les commandes shell et messages de commit en ASCII.
- Toujours nettoyer les worktrees après fusion (`git worktree remove` + `git branch -D`).
