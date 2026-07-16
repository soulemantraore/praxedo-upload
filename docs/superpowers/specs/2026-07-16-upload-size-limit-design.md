# Spec — Plafond d'upload configurable (1 Go) + alignement scanner + affichage front

- **Date** : 2026-07-16
- **Statut** : validé (design), en attente de plan d'implémentation
- **Composants touchés** : `praxedo-upload-backend`, `praxedo-upload-scanner`, `praxedo-upload-ui`, README racine

## 1. Contexte & problème

Aujourd'hui **aucune taille maximale d'upload n'est appliquée** :

- L'API valide uniquement `@Positive long size` sur `RegisterFileRequest` (borne basse : `size > 0`), sans plafond.
- L'upload part **directement vers GCS** via une URL signée V4 : les octets ne transitent jamais par l'app, et l'URL signée ne borne pas la taille réelle.
- Le scanner ClamAV impose de fait une limite *cachée* : la directive clamd `StreamMaxLength` (défaut **25 Mo**). Au-delà, clamd répond `ERROR` (« INSTREAM size limit exceeded ») → le scanner renvoie HTTP 502 → le worker enregistre `SCAN_FAILED` (jamais `CLEAN`). Un gros fichier n'est donc jamais servi, mais il ne peut pas non plus être traité.
- Le front (`UploadModal.tsx:160`) annonce « jusqu'à **2 Go** par fichier » — valeur **fausse et trompeuse** (aucune limite réelle, et incohérente avec le comportement).

**But** : introduire un plafond d'upload **explicite et configurable** (cible **1 Go**), relever les limites du scanner pour qu'un fichier de 1 Go soit réellement scannable, et rendre l'affichage front exact et cohérent.

### Point de vérité technique important

La contrainte de taille scannable **ne vient pas de la RAM du worker**. Le worker (Cloud Run, 1 Gio) n'envoie que le `gsUri` au scanner (`RemoteScannerClient`) — il ne télécharge jamais le fichier. Le scanner FastAPI (512 Mio) *streame* GCS → clamd sans bufferiser (« never fully buffered in memory », cf. `app/gcs.py`). La vraie limite dure est donc portée par **le sidecar ClamAV** : sa RAM (2 Gio actuellement) + la directive `StreamMaxLength`. La documentation justifiera la limite par **la capacité du service de scan**, pas par le worker.

## 2. Objectifs / non-objectifs

**Objectifs**
- Plafond d'upload configurable, défaut **1 Go**, appliqué à l'API (fichier seul + lot).
- Scanner capable de scanner un fichier jusqu'à 1 Go (limites clamd + RAM sidecar relevées).
- Front : affichage exact de la limite + pré-validation avant upload.
- README racine : documenter la limite, sa justification et la marche à suivre pour l'augmenter.

**Non-objectifs (YAGNI)**
- Enforcement dur au niveau de l'URL signée GCS (`content-length-range`) — noté en piste d'amélioration.
- Endpoint `/config` exposant la limite au front — noté en piste d'amélioration.
- Reprise/chunking d'upload, upload résumable GCS.

## 3. Décisions arrêtées

| # | Décision | Justification |
|---|----------|---------------|
| D1 | Plafond = **1 Go = 1 073 741 824 octets** (1 GiB, notation Spring `DataSize` `1GB`) | Cible demandée ; tient dans les ressources du service de scan. |
| D2 | **Enforcement = Option A** : validation de la taille *déclarée* côté API + pré-validation front. URL signée **non bornée**. | Simplicité, pas de rupture de contrat pour les systèmes tiers. La garantie de sécurité fondamentale tient : un fichier réellement trop gros pour le scan finit `SCAN_FAILED` → jamais servi. |
| D3 | RAM du **sidecar clamav : 2 → 4 Gio** | Marge pour la décompression d'archives d'un fichier de 1 Go (la base de signatures ~1-2 Go reste chargée). |
| D4 | Config clamd via **image ClamAV dérivée** (`clamd.conf` versionné) | Reproductible et versionné, vs surcharge à la volée. *(À confirmer : si l'image officielle accepte une variable d'env plus simple, la préférer.)* |
| D5 | Front : limite via **constante partagée** `MAX_UPLOAD_BYTES`, alignée manuellement avec le back | YAGNI ; un endpoint `/config` (source unique) est une amélioration future. |
| D6 | Réponse HTTP au dépassement = **413 Payload Too Large** | Sémantiquement correct pour un corps/charge trop volumineux. |

## 4. Design détaillé

### 4.1 Backend — `praxedo-upload-backend`

- **Propriété** : `storage.max-upload-size` (type Spring `DataSize`), défaut `1GB`, surchargeable via env `STORAGE_MAX_UPLOAD_SIZE`. Ajoutée à `StorageProperties` (le binding existant des propriétés `storage.*`).
- **Validation** : dans `FileUploadService.register(...)` (chemin commun aux uploads seuls et aux lots via `registerBatch`), rejeter si `cmd.sizeBytes() > maxUploadSize.toBytes()` en levant une exception métier dédiée (p. ex. `FileTooLargeException` dans le package d'exceptions du domaine/application, cohérent avec l'organisation existante).
  - Le `@Positive` du DTO `RegisterFileRequest` **reste** (borne basse). La borne haute est dynamique (dépend d'une propriété) → elle ne peut pas être une annotation `@Max` (qui exige une constante) et vit donc dans le service.
- **Mapping HTTP** : l'exception → **413 Payload Too Large**, via le `@RestControllerAdvice` existant (ou en l'étendant), avec un corps JSON cohérent avec le style d'erreurs actuel (message incluant la limite et la taille reçue).
- **Tests** :
  - Unitaire `FileUploadServiceTest` : accepte un fichier à la limite exacte ; rejette à `limite + 1`.
  - Unitaire lot : un item trop gros dans `registerBatch` est rejeté.
  - Web (MockMvc) : `POST /api/files` avec `size` > limite → **413** + corps d'erreur attendu.

### 4.2 Scanner — `praxedo-upload-scanner`

- **Directives clamd** relevées à **≥ 1 Go** (avec petite marge, p. ex. valeur alignée sur 1 Go + tolérance) :
  - `StreamMaxLength` (limite du flux INSTREAM)
  - `MaxFileSize` (taille max d'un fichier scanné)
  - `MaxScanSize` (volume total scanné ; doit rester ≥ `MaxFileSize`)
- **Application (D4)** : image dérivée `FROM clamav/clamav-debian` copiant un `clamd.conf` ajusté, buildée et poussée par le Makefile (`deploy/Makefile`), référencée via la variable `CLAMAV_IMAGE`. Vérifier au préalable si l'image officielle expose une variable d'environnement de surcharge (auquel cas la préférer pour éviter un artefact d'image supplémentaire).
- **Ressources** : sidecar `clamav` `--memory=2Gi` → **`4Gi`** dans `deploy/Makefile` (commande `gcloud run deploy`, flag scopé au `--container=clamav`).
- **Timeouts** : `CLAMAV_TIMEOUT=540`, scanner `--timeout=550`, ack Pub/Sub — déjà généreux ; **inchangés**, à surveiller si un scan de 1 Go les dépasse.
- **Doc** : mettre à jour la section « Piège connu » du `README.md` du scanner (les nouvelles valeurs, et le fait qu'elles sont désormais gérées par l'image dérivée / la config de déploiement).

### 4.3 Front — `praxedo-upload-ui`

- **Constante** `MAX_UPLOAD_BYTES` (dans `config.ts`), valeur = 1 073 741 824, avec un commentaire de rappel : **doit rester alignée avec `storage.max-upload-size` du backend**.
- **Affichage** : `UploadModal.tsx` (ligne ~160) : remplacer « jusqu'à 2 Go par fichier » par « jusqu'à **1 Go** par fichier » (libellé exact ; la valeur numérique vient de la constante / `formatBytes`).
- **Pré-validation** : dans `handleFiles` (ou en amont de `useUploadFiles`), écarter les fichiers dont `file.size > MAX_UPLOAD_BYTES` **avant** d'appeler `registerUpload`, et afficher un message via le bandeau d'erreurs existant (structure `UploadProgress.errors` : `{ filename, message }`). Les fichiers valides du même lot continuent d'être envoyés.

### 4.4 README racine

- Section « Choix techniques & hypothèses » : documenter le plafond actuel **1 Go**, justifié par **la capacité du service de scan** (sidecar ClamAV 2→4 Gio + `StreamMaxLength`), en précisant que le worker ne bufferise pas le fichier.
- Marche à suivre pour augmenter la limite : relever conjointement (1) `storage.max-upload-size`, (2) `StreamMaxLength`/`MaxFileSize`/`MaxScanSize`, (3) la RAM du sidecar clamav, (4) l'affichage `MAX_UPLOAD_BYTES` du front.

## 5. Cohérence & sécurité

- **Alignement des limites** : `MAX_UPLOAD_BYTES` (front) = `storage.max-upload-size` (API) ≤ `StreamMaxLength` ≤ `MaxScanSize` (scanner). Toute augmentation future doit préserver cet ordre, sinon un fichier accepté par l'API échouerait au scan (mauvaise UX).
- **Limite déclarative (D2)** : la validation API porte sur la taille *déclarée*. Un client pourrait déclarer moins et envoyer plus (l'URL signée ne borne pas). Conséquence bornée : au pire, un fichier plus gros que `StreamMaxLength` sera uploadé puis bloqué au scan (`SCAN_FAILED`) — **jamais servi**. L'invariant « aucun fichier non scanné/validé n'est servi » reste intact.
- **Abus/coût** : sans enforcement dur, un client peut consommer du stockage jusqu'à `StreamMaxLength`. Acceptable au MVP ; l'enforcement dur GCS (`content-length-range`) est la parade, listée en amélioration.

## 6. Points de vigilance / hypothèses

- Un scan de 1 Go peut être notablement plus lent que 25 Mo : surveiller les timeouts (540/550 s) lors du test e2e.
- 4 Gio pour le sidecar clamav est une estimation prudente ; à ajuster si l'OOM ou le sous-dimensionnement se manifeste.
- Vérifier le mode exact de surcharge de `clamd.conf` supporté par `clamav/clamav-debian` avant de figer l'approche image dérivée (D4).

## 7. Pistes d'amélioration (hors périmètre)

- Enforcement dur via URL signée GCS bornée (`X-Goog-Content-Length-Range`).
- Endpoint API `/config` exposant la limite → source unique pour le front.
- Upload résumable/chunké pour les très gros fichiers.

## 8. Plan de test / vérification

- Backend : tests unitaires + web ci-dessus (`mvn test`).
- Front : build + vérif manuelle de la pré-validation et du libellé.
- E2e (déployé) : upload d'un fichier proche de 1 Go → statut `CLEAN` (scan réussi) ; upload > 1 Go → **413** côté API et rejet côté UI. S'appuyer sur la skill `testing-praxedo-e2e`.
