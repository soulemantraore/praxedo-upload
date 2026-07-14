# Décisions d'architecture — praxedo-app (ADR light)

Journal des décisions prises pendant le brainstorming. Statut : ✅ actée · 🔄 en discussion · ❌ écartée.

## D1 — Niveau d'ambition ✅
**Décision :** viser un **système complet déployé sur GCP**, qui tourne de bout en bout (backend Spring Boot + React + antivirus réel + déploiement GCP live). Démo cliquable.
**Pourquoi :** une démo réelle end-to-end est la plus démonstrative et lève les doutes d'intégration. On calibre chaque couche pour rester propre (SOLID) sans sur-ingénierie.

## D2 — Antivirus ✅
**Décision :** définir une interface `AntivirusScanner` (Dependency Inversion / SOLID) avec **ClamAV comme implémentation réelle par défaut** (conteneur, appelé via clamd ou wrapper REST). Documenter qu'un adapter SaaS (Cloudmersive/MetaDefender) serait trivial à brancher.
**Pourquoi :** montre la conception SOLID (le domaine ne dépend pas de ClamAV mais d'une abstraction) tout en tournant réellement, gratuitement et en préservant la confidentialité (fichiers non exfiltrés). Un seul impl réel = pas de sur-ingénierie.

## D3 — Structure des dépôts ✅
**Décision :** **polyrepo** — deux dépôts Git séparés dans le dossier courant, déployés **séparément**, chacun avec **son propre `.gitignore`** :
- **`praxedo-upload-backend`** — Java / Spring Boot.
- **`praxedo-upload-ui`** — React.
**Pourquoi :** backend et frontend ont des cycles de déploiement, des runtimes et des pipelines indépendants sur GCP. Séparer clarifie les responsabilités et le CI/CD.
**À trancher plus tard :** où versionner la mémoire partagée `.claude/` (elle vit au-dessus des deux repos) — repo « meta » parent, ou commit dans le backend. Voir aussi `.superpowers/` (dossier de travail, à ignorer).
**Remplacée (2026-07-14) :** bascule en **monorepo** — voir **D16** (résout aussi le « à trancher » : `.claude/` est versionné à la racine du monorepo).

## D4 — Flux de scan : asynchrone ✅
**Décision :** flux **asynchrone**. `POST /files` stocke le fichier + métadonnées, répond immédiatement (202) avec un id et un statut `PENDING`. Un worker scanne en arrière-plan et fait évoluer le statut. Le download (`GET /files/{id}/content`) est **gated** : servi seulement si statut = `CLEAN`.
**Pourquoi :** répond aux deux contraintes (nombreux utilisateurs simultanés + tailles très variables). L'upload ne bloque jamais un thread sur la durée du scan. La garantie « pas servi sans scan » devient un invariant simple.

## D5 — Machine à états du cycle de vie ✅
**Décision :** `PENDING → SCANNING → { CLEAN | INFECTED | SCAN_FAILED }`.
- `CLEAN` = seul état téléchargeable (invariant de sécurité).
- `INFECTED` = octets supprimés/quarantaine, métadonnée + audit conservés ; download → 403.
- `SCAN_FAILED` = erreur technique (≠ menace) ; **retries bornés** avec backoff → retour à SCANNING ; au bout de N échecs reste FAILED (visible, non téléchargeable).
- `SCANNING` explicite pour l'observabilité + reprise après crash worker.
**Pourquoi :** peu de code en plus, beaucoup plus robuste et observable ; distingue clairement menace et incident technique.

## D6 — Topologie GCP : découplée via Pub/Sub ✅
**Décision :** Topologie A. `file-api` (Cloud Run) reçoit l'upload → écrit les octets dans **GCS**, une ligne PENDING dans **Cloud SQL (Postgres)**, publie sur **Pub/Sub**. Un `scan-worker` (Cloud Run) consomme, appelle **ClamAV** (conteneur **sidecar** du worker, `clamd` sur `localhost:3310` ; min 1 instance), met à jour le statut. Dead-letter topic pour SCAN_FAILED après N essais. `file-api` et `scan-worker` = même app Spring Boot, 2 services Cloud Run (profils `api`/`worker`).
**Raffinement :** ClamAV en sidecar plutôt qu'en service séparé, car Cloud Run est HTTP-only alors que `clamd` parle TCP → communication via localhost, pas besoin de wrapper HTTP.
**Alternative B évaluée puis écartée (2026-07-12) :** un service `clamav-scanner` autonome, déclenché par GCS, qui lit GCS et **rapporte** le verdict à file-api via un callback `/internal/scan-result`. Avantages : antivirus autonome/réutilisable, cloisonné (sans accès DB), scalable indépendamment. Coûts : +1 service, et surtout un **callback à sécuriser rigoureusement (OIDC/IAM) sinon bypass antivirus par faux « CLEAN »**. Scalabilité équivalente à A (plafond = ClamAV dans les deux cas). **Choix : A (sidecar) pour la simplicité** — le worker écrit le statut directement, pas de callback à sécuriser. `clamd` n'est jamais « dans notre code » : c'est l'image officielle `clamav/clamav` co-localisée, appelée via le port `AntivirusScanner`.
**Pourquoi :** répond à la concurrence (le scan n'engorge jamais l'API), retries bornés quasi gratuits (redélivraison Pub/Sub + DLQ), chaque service reste petit et mono-responsabilité, idiomatique GCP.

## D7 — Abstractions stockage & persistance (Dependency Inversion) ✅
**Décision (exigence utilisateur) :** créer des **ports (interfaces)** pour découpler le domaine de l'infrastructure.
- **`FileStorage`** : `store/read/delete`. Impl par défaut **`GcsFileStorage`** (GCS). Adapter **`LocalFileStorage`** (filesystem) pour dev/tests. Le domaine ne référence jamais le SDK GCS.
- **`FileMetadataRepository`** : port domaine, adapter **JPA/Postgres** par défaut (Cloud SQL). Changer de moteur SQL = changer l'**URL JDBC** (+ dialecte) via Spring Data JPA ; changer de paradigme (Firestore/Mongo) = nouvel adapter.
- Choix du provider par **configuration** (profils/properties Spring), injection par le conteneur IoC.
- Idiome : **interfaces** pour les ports (classes abstraites seulement si comportement partagé). Un seul adapter réel par port + un adapter dev = pas de sur-ingénierie.

## D8 — Gros fichiers : URLs signées direct-to-GCS ✅
**Décision :** le client échange les octets **directement avec GCS** via des **URLs signées** courtes ; l'app ne relaie jamais les gros octets.
- **Upload** : `POST /files` (métadonnées : nom, taille, type) → crée la ligne PENDING + renvoie `fileId` + **URL d'upload signée** → le client PUT les octets sur GCS.
- **Trigger du scan** : événement GCS **object finalize** → **Pub/Sub** → `scan-worker` (l'app ne déclenche plus le scan à la réception, puisqu'elle ne reçoit plus les octets).
- **Download** (si statut `CLEAN`) : l'API vérifie le statut puis renvoie une **URL de download signée courte**.
- **Port** : `FileStorage` expose `createUploadUrl` / `createDownloadUrl` ; `LocalFileStorage` retombe sur des endpoints proxy → abstraction préservée côté client.
**Pourquoi :** répond à « tailles très variables » + « nombreux utilisateurs », offload la bande passante hors de Cloud Run, tout en gardant l'invariant de sécurité (URL de download émise uniquement sur `CLEAN`).
**À gérer :** lignes PENDING orphelines (URL demandée mais upload jamais fait) → TTL/réconciliation → statut EXPIRED.

## D9 — Authentification : clés API par-client + propriété des fichiers ✅
**Décision :** authentification par **clé API dans un header** (`X-API-Key`), vérifiée par un filtre Spring Security. Les clés sont **par-client** : une clé est générée pour chaque système tiers / utilisateur d'API et identifie un **owner (ApiClient)**.
- Chaque fichier est **rattaché à son owner** (le client qui l'a créé) → chaque client ne voit/télécharge **que ses fichiers** (scoping automatique sur toutes les routes ; sinon 404/403).
- Clés stockées **hachées** en base (table `api_client`), jamais en clair. Résolution owner = hash de la clé.
- Génération des clés pour la démo : seed via config / cible Makefile ; endpoint admin protégé documenté comme évolution.
**À documenter (README) :** pour des **utilisateurs humains connectés**, ajouter **OAuth2/JWT** (coexiste avec les clés API machine-to-machine) pour gérer finement les accès.
**Pourquoi :** répond directement au besoin « systèmes tiers » de l'énoncé ; multi-tenant léger (owner + scoping), sans sur-ingénierie.

## D13 — Opérations par lot (batch) ✅
**Décision (exigence utilisateur) :** un système tiers peut **envoyer plusieurs fichiers en batch** et **suivre les statuts de la liste**.
- `POST /api/batches` : corps `{ files: [{filename, contentType, size}, ...] }` → `201 { batchId, items: [{id, filename, status: PENDING, uploadUrl, uploadExpiresAt}, ...] }`. Le client PUT ensuite chaque fichier vers son URL signée.
- `GET /api/batches/{batchId}` : statuts de tous les fichiers du lot + résumé par statut. Scopé à l'owner.
- Chaque fichier est scanné **indépendamment** ; le `batchId` (colonne nullable sur `files`) sert juste de regroupement — pas d'agrégat lourd (pas de sur-ingénierie).
**Pourquoi :** cas d'usage explicite (intégration API tierce en batch).

## D10 — Frontend : suivre la maquette Cloud Design fournie ✅
**Décision :** le frontend React **suit la maquette Cloud Design** fournie par l'utilisateur (fichier « Fichiers sécurisés.dc.html », design tool claude.ai).
**Spécifications visuelles retenues :**
- Design system **bleu/vert corporate**, portail brandé (`portalName` = « Praxedo » dans la maquette, paramétrable), variante claire « Clair & aéré » par défaut (fond clair, cartes blanches, accents colorés) ; variante « Rail sombre » existe.
- **One-pager** : en-tête (logo bouclier + nom portail, bouton « Déposer un fichier », avatar utilisateur) + **4 cartes de métriques** (Total fichiers / Validés=vert / En analyse=bleu / Bloqués=rouge) + **tableau paginé** (6 par page) avec **recherche**.
- **Tableau « Mes fichiers »** : colonnes NOM / TAILLE / AJOUTÉ LE / STATUT / ACTION ; badge de type de fichier coloré ; **icône de téléchargement affichée uniquement pour les fichiers Validés (CLEAN)** ; lien « Voir ».
- **Badges de statut** (alignés sur la machine à états) : Validé=CLEAN, « Scan en cours »=SCANNING (+ barre de progression), « En attente »=PENDING, Bloqué=INFECTED.
- **Dépôt en pop-up** (modale) : zone glisser-déposer + parcourir, un ou plusieurs fichiers.
- **Rapport antivirus en modale** au clic sur un fichier : verdict antivirus + actions télécharger / rescanner (« cœur de l'exercice »).
**Implications backend :** l'API doit exposer stats/compteurs par statut, liste paginée + recherche, et le détail (verdict) d'un fichier.
**Pour l'implémentation :** récupérer la source exacte du `.dc.html` (export depuis le design tool) pour une reproduction fidèle ; captures d'écran de référence prises pendant le brainstorming.

## D11 — IaC & déploiement : gcloud + Makefile (pas de Terraform) ✅
**Décision (exigence utilisateur) :** **pas de Terraform**. Le provisioning et le déploiement de l'infra GCP se font via des **commandes `gcloud`** :
- dans la **CI/CD** (GitHub Actions) pour le build + déploiement continu ;
- via un **`Makefile`** (côté infra) avec des cibles pour déployer l'infrastructure **manuellement** (ex. `make infra-up`, `make deploy-api`, `make deploy-worker`, etc.).
**Pourquoi :** garde l'infra simple et lisible, sans outil supplémentaire ; les commandes gcloud sont explicites et reproductibles ; le Makefile documente et automatise les étapes manuelles.

## D12 — Testabilité & injection de dépendances ✅
**Décision (exigence utilisateur) :** vraie **injection de dépendances** pour tout rendre testable ; suivre les bonnes pratiques ; **ne pas être bloqué par Spring** pour tester la logique (pas de contexte Spring requis pour les tests unitaires du domaine/application). Détail et décisions « de la même famille » dans [[bonnes-pratiques]] (injection par constructeur, domaine POJO sans annotations, pyramide de tests, adapters in-memory comme doubles, injection de `Clock`/IdGenerator, config typée, etc.).

## D14 — Lombok (backend) ✅
**Décision (exigence utilisateur) :** ajouter **Lombok** au backend (dépendance `optional`, exclue du fat-jar via `spring-boot-maven-plugin`). Premier usage : **`@Builder` sur `FileRecord`** (Task 04).
**Cadre d'usage :** Lombok est **build-time uniquement** (annotations non retenues à l'exécution) → compatible avec « domaine sans framework runtime » ([[bonnes-pratiques]]). Sur une **entité à invariants** (`FileRecord`), le `@Builder` reste **encapsulé par les fabriques** `pending()`/`rehydrate()` (états initiaux cohérents) plutôt qu'exposé comme seule voie de construction. Les **value objects simples** restent des `record` (déjà concis) ; pas de sur-usage de Lombok.
**Getters (convention adoptée) :** sur les **entités**, générer les accesseurs via **`@Getter` + `@Accessors(fluent = true)`** → API « record-like » (`id()`, `status()`, …), zéro boilerplate. Ne **pas** utiliser le `@Getter` JavaBean (`getId()`) qui casserait la convention attendue par tests/services. Note : `@Accessors` est dans `lombok.experimental` (stable, largement utilisé). Les **VO simples** restent des `record`.

## D15 — Scanner externalisé : service HTTP privé appelé en synchrone (raffine/supersède D6 sur le sidecar) ✅ (2026-07-14)
**Décision :** sortir ClamAV du worker Spring vers un **service de scan autonome** (nouveau repo `praxedo-upload-scanner`, Python/FastAPI + sidecar ClamAV), déployé comme **Cloud Run Service HTTP privé**. Nouveau flux : GCS `OBJECT_FINALIZE` → Pub/Sub → worker Spring `/internal/scan-events` → le worker **appelle** le scanner en HTTP (`POST /scan {gsUri}`, jeton **OIDC** service-à-service) → le scanner lit GCS, scanne, **renvoie** `{infected, engine, threatName}` → **le worker (et lui seul) écrit le verdict** en base → ack.
**Topologie : 3 services Cloud Run** — `praxedo-api` (public) + `praxedo-worker` (privé, désormais **léger** : plus de sidecar ClamAV, `min-instances=0`) + `praxedo-scanner` (privé, `min-instances=1` pour garder la base de signatures chaude). Les deux premiers = même image Spring (profils) ; le scanner = repo/langage séparés.
**Pourquoi ceci lève l'objection qui avait fait écarter l'« Alternative B » de [[decisions-archi]] D6 :** B rapportait le verdict via un **callback entrant** `/internal/scan-result` → un attaquant pouvait usurper un faux « CLEAN » (bypass antivirus). Ici le scanner **n'écrit jamais en base** et n'expose **aucun endpoint de résultat** : le verdict ne revient qu'en **réponse synchrone à un appel sortant** que le worker initie vers un service privé (ingress interne, seul le SA du worker a `run.invoker`). Il n'existe donc **aucune surface entrante usurpable** ; le niveau de confiance est le même qu'avant (le worker faisait déjà confiance au verdict de clamd en localhost). L'invariant « servi seulement si `CLEAN` » reste porté par le seul `FileScanService`.
**Bénéfices vs sidecar (D6) :** antivirus **autonome et réutilisable**, cloisonné (le scanner n'a que `roles/storage.objectViewer`, aucun accès base), scalable indépendamment ; le worker redevient bon marché (scale-to-zero). Le port reste `AntivirusScanner` — seule la signature passe de `scan(InputStream,…)` à `scan(String storageKey,…)` (l'adapter gcp = `RemoteScannerClient`, l'adapter local/test = `FakeAntivirusScanner` lisant via `FileStorage`).
**Coûts assumés :** +1 service et +1 saut réseau ; échec technique (scanner injoignable/5xx/corps illisible/`infected` sans `threatName`) → `ScanException` → `SCAN_FAILED`, **jamais** un faux `CLEAN`. Budgets de timeout emboîtés : `push ack (600s) ≥ worker read-timeout ≥ scanner ≥ scan clamd`.
**Statut de D6 :** le raffinement « sidecar » de D6 est **remplacé** par D15 ; le reste de D6 (découplage Pub/Sub, DLQ, api/worker = même image) reste valable. Détail infra dans [[infra]].

## D16 — Bascule polyrepo -> monorepo (remplace D3) ✅ (2026-07-14)
**Décision :** regrouper les **trois** composants dans un **monorepo unique** `praxedo-upload` (github.com/soulemantraore/praxedo-upload, public), au lieu des dépôts séparés de D3. Import via **`git subtree`** depuis la branche d'intégration `develop` (backend, ui) / `main` (scanner) → **historique complet préservé** (chaque composant : `git log -- praxedo-upload-<x>/`). Les 3 anciens dépôts sont conservés comme **archives en lecture seule** (sauvegarde).
**Déploiement séparé conservé :** un workflow GitHub Actions **par composant**, à la racine `.github/workflows/`, **filtré par `paths:`** — un push ne redéploie que le composant dont le sous-dossier change (backend/ui sur push `main` ; scanner en `workflow_dispatch` tant que son bootstrap GCP n'est pas fait). Le premier push initial ne déclenche aucun run (GitHub n'évalue pas `paths` à la création de branche).
**Pourquoi :** le livrable attendu est « **un** dépôt Git public + README » (une seule porte d'entrée). Le monorepo simplifie la vue d'ensemble et **résout le « à trancher » de D3** : la mémoire partagée `.claude/` est désormais versionnée à la racine du monorepo. Le déploiement indépendant reste garanti par le path-filtering → aucun compromis vs le polyrepo.
**Notes :** placeholder `PROJECT_ID = my-gcp-project` conservé côté public (valeurs surchargées au déploiement). **Finitions restantes :** configurer les secrets WIF du monorepo (réactiver la CI), archiver les 3 anciens repos, supprimer les sources locales de secours. Spec : `docs/superpowers/specs/2026-07-14-monorepo-praxedo-upload-design.md` ; plan : `docs/superpowers/plans/2026-07-14-monorepo-praxedo-upload.md`.

## D17 — Base de données : Supabase Postgres au lieu de Cloud SQL (raffine D6/D7) 🔄 (2026-07-14)
**Décision (orientation utilisateur) :** utiliser **Supabase (Postgres managé)** comme base applicative, à la place de **Cloud SQL (Postgres)** retenu en D6. Le port `FileMetadataRepository`/`ApiClientRepository` et l'adapter **JPA/Postgres** restent inchangés (D7 : « changer de moteur SQL = changer l'URL JDBC ») — Supabase étant du Postgres standard, Flyway et Spring Data JPA fonctionnent tels quels.
**Implications à traiter (non encore implémentées) :** connexion via la **connection string Supabase** (pooler PgBouncer, port 6543 en transaction-pooling, ou 5432 en direct) au lieu du **Cloud SQL Auth Proxy sidecar** → suppression du sidecar `cloud-sql-proxy` du worker/api (`worker-service.yaml`, `api-service.yaml`), maj `application-gcp.yml` (URL JDBC + credentials), secret du mot de passe DB via Secret Manager, cibles `deploy/Makefile` (plus de provisioning Cloud SQL). SSL requis côté Supabase (`sslmode=require`). Points à trancher : pooler vs connexion directe, gestion des migrations Flyway (mode transaction-pooling ne supporte pas certains features → préférer session-pooling/direct pour Flyway).
**Implémentation minimale faite (2026-07-14) :** `api/worker-service.yaml` sans sidecar Cloud SQL Auth Proxy (conteneur unique `app`, `DB_URL` direct), `deploy/Makefile` (variable `DB_URL`, cible `sql` + rôles `cloudsql.client` supprimés, `secrets` exige `DB_PASSWORD` Supabase, `enable-apis` sans `sqladmin`), `application-gcp.yml`/`.env.example` (commentaires), README backend + `deploy/README.md` + `docs/architecture.html` (schémas). Backend compile OK. **Reste (non fait) :** renseigner les vraies valeurs `DB_URL`/`DB_USER`/`DB_PASSWORD` Supabase, choisir pooler vs direct pour Flyway, et **tester un déploiement réel** (aucun accès GCP/Supabase côté agent). Voir [[infra]].
**Défauts d'implémentation validés :** tests (JUnit 5 + Testcontainers Postgres ; pytest + clamd pour le scanner), CI/CD (GitHub Actions + gcloud → Cloud Run), observabilité (logs structurés → Cloud Logging), config (profils Spring local/gcp + Secret Manager).
_Toutes les décisions sont prises. Spec rédigée dans docs/superpowers/specs/._
