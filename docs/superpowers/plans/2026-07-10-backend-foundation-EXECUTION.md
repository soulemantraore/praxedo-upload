# Backend jalon 1 — Ordonnancement d'exécution (DAG + workflow PR)

Complément d'exécution du plan `2026-07-10-backend-foundation.md`.

## Workflow (exigence utilisateur)
- **Une branche + une PR par tâche**, vers **`develop`**. Le mainteneur relit et fusionne.
- Tâches **indépendantes** d'une même vague → réalisées **en parallèle** (git worktrees isolés).
- Tâches **dépendantes** → **bloquées** jusqu'à la fusion de leur(s) prérequis (colonne « Bloquée par »).
- Après l'ouverture des PR d'une vague, on **s'arrête** : le mainteneur fusionne, puis on repart de `develop` à jour pour la vague suivante.

## Build
Java 21 n'est pas le JDK par défaut → toujours préfixer :
```
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn ... test
```

## Convention de branche
`task/NN-slug` (ex. `task/02-file-status`). PR titrée `Task NN — <titre>`, base `develop`.

## Ajustement vs le plan (config wiring)
Pour que **chaque état fusionné reste vert** (le test `contextLoads` charge le contexte Spring dès que des `@Service` nécessitant un `Clock` existent), la **configuration transverse est avancée dans la Task 1** :
- `UploadBackendApplication` porte `@ConfigurationPropertiesScan`, `@EnableAsync`, `@EnableScheduling`.
- Un `ClockConfig` (Task 1) fournit le bean `Clock` (`Clock.systemUTC()`).
- `StorageProperties` (Task 8) est annoté `@ConfigurationProperties` → détecté par `@ConfigurationPropertiesScan` (plus besoin de `@EnableConfigurationProperties`).
- **Task 13** ne contient donc **plus** `AppConfig` : seulement `ApiKeyService` + `LocalSeedConfig`.
- Task 1 ne recrée pas `.gitignore`/`README.md` (déjà sur `main`/`develop`).

## Vagues & DAG

| Vague | Tâche | Branche | Bloquée par | Parallèle avec | Statut |
|------|-------|---------|-------------|----------------|--------|
| 1 | T1 Scaffold (+ config wiring, Clock) | `task/01-scaffold` | — | — | 🟨 PR #1 (à fusionner) |
| 2 | T2 FileStatus | `task/02-file-status` | T1 ✅ | T3 | 🟨 PR #2 (à fusionner) |
| 2 | T3 ScanVerdict | `task/03-scan-verdict` | T1 ✅ | T2 | 🟨 PR #3 (à fusionner) |
| 3 | T4 FileRecord | `task/04-file-record` | T2 ✅, T3 ✅ | T5 | 🟨 PR #4 (à fusionner) |
| 3 | T5 Value objects + ApiClient | `task/05-value-objects` | T2 ✅ | T4 | 🟨 PR #5 (à fusionner) |
| 4 | T6 Ports | `task/06-ports` | T2..T5 ✅ | — | 🟨 PR #6 (à fusionner) |
| 5 | T7 In-memory repos + IdGen | `task/07-inmemory-repos` | T6 ✅ | T8, T9 | 🟨 PR #7 (à fusionner) |
| 5 | T8 LocalFileStorage + props | `task/08-local-storage` | T6 ✅ | T7, T9 | 🟨 PR #8 (à fusionner) |
| 5 | T9 FakeAntivirusScanner | `task/09-fake-scanner` | T6 ✅ | T7, T8 | 🟨 PR #9 (à fusionner) |
| 6 | T10 FileUploadService | `task/10-upload-service` | T7,T8, fix#10 | T11,T12,T13 | 🟨 PR #11 (à fusionner) |
| 6 | T11 FileScanService + queues | `task/11-scan-service` | T7,T8,T9, fix#10 | T10,T12,T13 | 🟨 PR #12 (à fusionner) |
| 6 | T12 Query/Download/Reconcile | `task/12-query-download` | T7,T8, fix#10 | T10,T11,T13 | 🟨 PR #13 (à fusionner) |
| 6 | T13 ApiKeyService + seed | `task/13-apikey-service` | T7, fix#10 | T10,T11,T12 | 🟨 PR #14 (à fusionner) |
| 7 | T14 Security (filter+config) | `task/14-security` | T13 ✅ | — | 🟨 PR #15 (à fusionner) |
| 8 | T15 Controllers + handler + proxy | `task/15-controllers` | T10,T11,T12,T14 ✅ | — | 🟨 PR #16 (à fusionner) |
| 9 | T16 E2E + README backend | `task/16-e2e-readme` | T15 ✅ | — | 🟨 PR #17 (à fusionner) |

Légende statut : ⬜ à faire/bloquée · 🟦 en cours · 🟨 PR ouverte (à fusionner) · ✅ fusionnée.

## Notes de suivi des PR
_(mis à jour au fil de l'eau : numéro de PR, SHA de fusion.)_
- **PR #1** — Task 01 Scaffold — ✅ fusionnée (develop @ cc7ea76). Worktree nettoyé.
- **PR #2** — Task 02 FileStatus — ✅ fusionnée (+ refactor `Map.ofEntries`). Worktree nettoyé.
- **PR #3** — Task 03 ScanVerdict — ✅ fusionnée. Worktree nettoyé.
- **PR #4** — Task 04 FileRecord (+ **Lombok `@Builder`** + accesseurs `@Getter`/`@Accessors(fluent)`) — ✅ fusionnée. Worktree nettoyé.
- **PR #5** — Task 05 value objects + ApiClient — ✅ fusionnée. Worktree nettoyé.
- **PR #6** — Task 06 Ports — ✅ fusionnée. Worktree nettoyé.
- **PR #7** — Task 07 repositories in-memory + UuidIdGenerator (subagent) — base `develop` — 🟨 en attente de fusion.
- **PR #8** — Task 08 LocalFileStorage + StorageProperties (subagent) — base `develop` — 🟨 en attente de fusion.
- **PR #9** — Task 09 FakeAntivirusScanner (subagent) — base `develop` — 🟨 en attente de fusion.
- **PR #7/#8/#9** — Vague 5 — ✅ fusionnées. Worktrees nettoyés.
- **PR #10 (fix)** — `@ActiveProfiles("test")` sur `contextLoads` — base `develop` — 🟨 **à fusionner EN PREMIER**. Débloque toute la Vague 6.
- **PR #10 (fix)** — `@ActiveProfiles("test")` — ✅ fusionnée. Débloque la Vague 6.
- **PR #11** — Task 10 FileUploadService — 🟨 à fusionner (27 tests).
- **PR #12** — Task 11 FileScanService + queues — 🟨 à fusionner (28 tests).
- **PR #13** — Task 12 query/download/reconcile (normalisée, sans son `application.yml`) — 🟨 à fusionner (29 tests).
- **PR #14** — Task 13 ApiKeyService + seed — 🟨 à fusionner (28 tests). Débloque T14 (sécurité).
- **PR #11/#12/#13/#14** — Vague 6 — ✅ fusionnées. Worktrees nettoyés.
- **PR #15** — Task 14 sécurité (filtre clé API + SecurityConfig, 401 entry point) — 🟨 à fusionner (39 tests). Débloque T15.
- **PR #16** — Task 15 contrôleurs REST + proxy local + handler d'exceptions (+ DTOs regroupés dans `UploadRequests`) — ✅ fusionnée.
- **PR #17** — Task 16 e2e (infecté bloqué, isolation owner) + README backend — 🟨 à fusionner (44 tests). **Dernière tâche du jalon 1.**

## Jalon 1 — quasi terminé
16 tâches implémentées (une PR chacune) + 1 PR de fix (#10). Backend testable **entièrement en local sans GCP** : domaine + application + web + sécurité + adapters in-memory/local/fake, 44 tests. Reste à fusionner #17.
**Jalons suivants** : 2 (ClamAV/Pub/Sub réels), 3 (adapters GCP GCS/Cloud SQL), puis frontend (`praxedo-upload-ui`) et infra (gcloud + Makefile + CI/CD).
