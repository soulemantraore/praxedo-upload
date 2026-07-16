# Plafond d'upload configurable (1 Go) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduire un plafond d'upload configurable (defaut 1 Go) applique par l'API, relever les limites du scanner ClamAV pour qu'un fichier de 1 Go soit reellement scannable, et rendre l'affichage front exact avec une pre-validation.

**Architecture:** L'API valide la taille *declaree* dans `FileUploadService` (chemin commun upload seul + lot) et leve `FileTooLargeException` (mappee en 413). Le scanner utilise une image ClamAV derivee dont le `clamd.conf` releve `StreamMaxLength`/`MaxFileSize`/`MaxScanSize`, avec un sidecar a 4 Gio. Le front lit une constante `MAX_UPLOAD_BYTES` alignee manuellement avec le backend, affiche la limite et rejette les fichiers trop gros avant l'upload.

**Tech Stack:** Java 21 / Spring Boot (Maven), Python / FastAPI + ClamAV (Docker, gcloud/Make), React + TypeScript (Vite, pnpm).

**Spec de reference:** `docs/superpowers/specs/2026-07-16-upload-size-limit-design.md`

**Rappel commandes:**
- Backend build/test : `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn -f praxedo-upload-backend/pom.xml test`
- Messages de commit et commandes shell en **ASCII** (validateur), pas de sequence `===`.

---

## File Structure

**Backend (`praxedo-upload-backend`)**
- Create `src/main/java/com/praxedo/upload/domain/exceptions/FileTooLargeException.java` — exception metier (taille > plafond).
- Modify `src/main/java/com/praxedo/upload/application/FileUploadService.java` — injection du plafond + validation dans `register(...)`.
- Modify `src/main/java/com/praxedo/upload/infrastructure/web/helpers/GlobalExceptionHandler.java` — mapping 413.
- Modify `src/main/resources/application.yml` — propriete `storage.max-upload-size`.
- Modify `src/test/java/com/praxedo/upload/application/FileUploadServiceTest.java` — tests unitaires + mise a jour du constructeur.
- Modify `src/test/java/com/praxedo/upload/infrastructure/web/FilesControllerTest.java` — test web 413.

**Scanner (`praxedo-upload-scanner`)**
- Create `deploy/clamav/Dockerfile` — image ClamAV derivee (limites relevees).
- Modify `deploy/Makefile` — cible `build-push-clamav`, variable image, sidecar `--memory=4Gi`.
- Modify `README.md` — section « Piege connu » (nouvelles valeurs).

**Front (`praxedo-upload-ui`)**
- Modify `src/config.ts` — constante `MAX_UPLOAD_BYTES`.
- Modify `src/components/UploadModal.tsx` — libelle + pre-validation.

**Racine**
- Modify `README.md` — sections « Choix techniques & hypotheses » et « Pistes d'amelioration ».

---

## Task 1: Backend — plafond configurable + 413 (TDD)

**Files:**
- Create: `praxedo-upload-backend/src/main/java/com/praxedo/upload/domain/exceptions/FileTooLargeException.java`
- Modify: `praxedo-upload-backend/src/main/java/com/praxedo/upload/application/FileUploadService.java`
- Modify: `praxedo-upload-backend/src/main/java/com/praxedo/upload/infrastructure/web/helpers/GlobalExceptionHandler.java`
- Modify: `praxedo-upload-backend/src/main/resources/application.yml`
- Test: `praxedo-upload-backend/src/test/java/com/praxedo/upload/application/FileUploadServiceTest.java`
- Test: `praxedo-upload-backend/src/test/java/com/praxedo/upload/infrastructure/web/FilesControllerTest.java`

- [ ] **Step 1: Ecrire les tests unitaires qui echouent (FileUploadServiceTest)**

Mettre a jour l'instanciation existante (ligne 25) pour ajouter le 5e parametre, et ajouter deux tests. Nouveaux imports en tete de fichier :

```java
import com.praxedo.upload.domain.exceptions.FileTooLargeException;
import org.springframework.util.unit.DataSize;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
```

Remplacer la ligne du service (ligne 25) :

```java
    private final FileUploadService service = new FileUploadService(repo, storage, ids, clock, DataSize.ofGigabytes(1));
```

Ajouter, avant l'accolade fermante de la classe :

```java
    @Test
    void rejects_upload_over_max_size() {
        var small = new FileUploadService(repo, storage, ids, clock, DataSize.ofBytes(100));
        assertThatThrownBy(() -> small.registerUpload(owner,
                new RegisterUploadCommand("big.bin", "application/octet-stream", 101L)))
            .isInstanceOf(FileTooLargeException.class);
    }

    @Test
    void accepts_upload_at_exact_max_size() {
        var small = new FileUploadService(repo, storage, ids, clock, DataSize.ofBytes(100));
        var result = small.registerUpload(owner,
                new RegisterUploadCommand("ok.bin", "application/octet-stream", 100L));
        assertThat(result.status()).isEqualTo(FileStatus.PENDING);
    }
```

- [ ] **Step 2: Lancer les tests unitaires pour verifier l'echec de compilation**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn -f praxedo-upload-backend/pom.xml test -Dtest=FileUploadServiceTest`
Expected: ECHEC de compilation — `FileTooLargeException` introuvable et constructeur `FileUploadService` a 5 arguments inexistant.

- [ ] **Step 3: Creer l'exception FileTooLargeException**

Fichier `praxedo-upload-backend/src/main/java/com/praxedo/upload/domain/exceptions/FileTooLargeException.java` :

```java
package com.praxedo.upload.domain.exceptions;

/** Taille declaree superieure au plafond autorise. Mappee en 413 par la couche web. */
public class FileTooLargeException extends RuntimeException {

    public FileTooLargeException(long sizeBytes, long maxBytes) {
        super("Fichier trop volumineux : " + sizeBytes + " octets (max " + maxBytes + " octets).");
    }
}
```

- [ ] **Step 4: Ajouter l'injection du plafond + la validation dans FileUploadService**

Ajouter les imports :

```java
import com.praxedo.upload.domain.exceptions.FileTooLargeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.unit.DataSize;
```

Ajouter le champ (a cote des autres champs `final`) :

```java
    private final long maxUploadBytes;
```

Remplacer le constructeur existant (lignes 24-29) par :

```java
    public FileUploadService(FileMetadataRepository repository, FileStorage storage, IdGenerator ids, Clock clock,
                             @Value("${storage.max-upload-size}") DataSize maxUploadSize) {
        this.repository = repository;
        this.storage = storage;
        this.ids = ids;
        this.clock = clock;
        this.maxUploadBytes = maxUploadSize.toBytes();
    }
```

Dans la methode privee `register(...)`, ajouter la validation en toute premiere instruction (avant `UUID id = ids.newId();`) :

```java
        if (cmd.sizeBytes() > maxUploadBytes) {
            throw new FileTooLargeException(cmd.sizeBytes(), maxUploadBytes);
        }
```

Note : `register(...)` est le chemin commun a `registerUpload` et a `registerBatch` (appele par item), donc la validation couvre aussi les lots.

- [ ] **Step 5: Declarer la propriete dans application.yml**

Dans `praxedo-upload-backend/src/main/resources/application.yml`, ajouter la ligne `max-upload-size` sous le bloc `storage:` (juste apres le bloc `local:`) :

```yaml
storage:
  local:
    base-dir: ${STORAGE_LOCAL_DIR:./data/uploads}
  # Plafond d'upload (taille declaree). Doit rester <= StreamMaxLength du scanner ClamAV.
  max-upload-size: ${STORAGE_MAX_UPLOAD_SIZE:1GB}
  upload-url-ttl: PT15M
  download-url-ttl: PT5M
  public-base-url: ${PUBLIC_BASE_URL:http://localhost:8080}
```

Cette propriete est heritee par le profil `test` (merge par-dessus `application.yml`), donc tous les `@SpringBootTest` chargent le contexte sans modification de `application-test.yml`.

- [ ] **Step 6: Lancer les tests unitaires — doivent passer**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn -f praxedo-upload-backend/pom.xml test -Dtest=FileUploadServiceTest`
Expected: PASS (5 tests).

- [ ] **Step 7: Lancer TOUTE la suite backend (garde-fou contexte Spring)**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn -f praxedo-upload-backend/pom.xml test`
Expected: PASS. Verifie qu'aucun autre `@SpringBootTest` n'est casse par l'ajout du `@Value` (la propriete est presente dans `application.yml`).

- [ ] **Step 8: Commit**

```bash
git add praxedo-upload-backend/src/main/java/com/praxedo/upload/domain/exceptions/FileTooLargeException.java \
        praxedo-upload-backend/src/main/java/com/praxedo/upload/application/FileUploadService.java \
        praxedo-upload-backend/src/main/resources/application.yml \
        praxedo-upload-backend/src/test/java/com/praxedo/upload/application/FileUploadServiceTest.java
git commit -m "feat(backend): reject uploads over configurable max size"
```

- [ ] **Step 9: Ecrire le test web 413 (FilesControllerTest) — doit echouer**

Ajouter dans `FilesControllerTest`, avant l'accolade fermante de la classe :

```java
    @Test
    void register_upload_over_max_size_returns_413() throws Exception {
        String body = "{\"filename\":\"big.bin\",\"contentType\":\"application/octet-stream\",\"size\":2000000000}";
        mvc.perform(post("/api/files").header("X-API-Key", key)
                .contentType("application/json").content(body))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.error").exists());
    }
```

`2000000000` octets (~2 Go) depasse le defaut `1GB` (1 073 741 824). Aucun import a ajouter (`post`, `status`, `jsonPath` deja importes).

- [ ] **Step 10: Lancer le test web — doit echouer avec 500**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn -f praxedo-upload-backend/pom.xml test -Dtest=FilesControllerTest`
Expected: ECHEC — statut 500 (l'exception remonte sans handler) au lieu de 413.

- [ ] **Step 11: Ajouter le handler 413 dans GlobalExceptionHandler**

Ajouter l'import :

```java
import com.praxedo.upload.domain.exceptions.FileTooLargeException;
```

Ajouter la methode dans la classe :

```java
    @ExceptionHandler(FileTooLargeException.class)
    public ResponseEntity<Map<String, String>> tooLarge(FileTooLargeException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of("error", e.getMessage()));
    }
```

- [ ] **Step 12: Lancer le test web — doit passer**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn -f praxedo-upload-backend/pom.xml test -Dtest=FilesControllerTest`
Expected: PASS.

- [ ] **Step 13: Commit**

```bash
git add praxedo-upload-backend/src/main/java/com/praxedo/upload/infrastructure/web/helpers/GlobalExceptionHandler.java \
        praxedo-upload-backend/src/test/java/com/praxedo/upload/infrastructure/web/FilesControllerTest.java
git commit -m "feat(backend): map FileTooLargeException to HTTP 413"
```

---

## Task 2: Scanner — relever les limites ClamAV + RAM du sidecar

**Files:**
- Create: `praxedo-upload-scanner/deploy/clamav/Dockerfile`
- Modify: `praxedo-upload-scanner/deploy/Makefile`
- Modify: `praxedo-upload-scanner/README.md`

Pas de test unitaire (changement de configuration d'infrastructure). La verification se fait par build Docker + inspection du `clamd.conf` effectif.

- [ ] **Step 1: Creer l'image ClamAV derivee**

Fichier `praxedo-upload-scanner/deploy/clamav/Dockerfile` :

```dockerfile
# Image ClamAV derivee : releve les limites de taille de clamd pour supporter des
# fichiers jusqu'a ~1 Go (defaut StreamMaxLength = 25M). Utilisee comme sidecar du
# scanner (voir ../Makefile, cible build-push-clamav). 1200M laisse une marge
# au-dessus du plafond applicatif de 1 Go.
FROM clamav/clamav-debian:latest

# clamd refuse les directives dupliquees : on remplace la ligne existante (commentee
# ou non) si presente, sinon on l'ajoute. Echoue explicitement si la conf de base est
# absente (plutot que de produire une conf tronquee qui casserait clamd).
RUN CONF=/etc/clamav/clamd.conf; \
    test -f "$CONF" || { echo "ERREUR: $CONF absent de l'image de base"; exit 1; }; \
    for kv in "StreamMaxLength 1200M" "MaxFileSize 1200M" "MaxScanSize 1200M"; do \
      key="${kv%% *}"; \
      if grep -qiE "^[#[:space:]]*${key}[[:space:]]" "$CONF"; then \
        sed -i -E "s|^[#[:space:]]*${key}[[:space:]].*|${kv}|I" "$CONF"; \
      else \
        printf '%s\n' "$kv" >> "$CONF"; \
      fi; \
    done
```

- [ ] **Step 2: Verifier le build et le contenu effectif du clamd.conf**

Run:
```bash
docker build -t praxedo-clamav-verify praxedo-upload-scanner/deploy/clamav
docker run --rm --entrypoint sh praxedo-clamav-verify -c "grep -iE 'StreamMaxLength|MaxFileSize|MaxScanSize' /etc/clamav/clamd.conf"
```
Expected: le build reussit et le `grep` affiche exactement les trois lignes `StreamMaxLength 1200M`, `MaxFileSize 1200M`, `MaxScanSize 1200M` (aucun doublon, aucune ligne commentee residuelle).
Si le build echoue sur « $CONF absent » : l'image de base genere sa conf au runtime — se rabattre sur un `clamd.conf` complet copie dans l'image (fallback documente dans la spec, section 6).

- [ ] **Step 3: Cabler l'image derivee dans le Makefile**

Dans `praxedo-upload-scanner/deploy/Makefile` :

a) Supprimer la ligne 42-43 existante :
```makefile
# ---- Sidecar image ----
CLAMAV_IMAGE     ?= clamav/clamav-debian:latest
```

b) Dans la section `# ---- Derived ----` (juste apres `IMAGE_URI := ...`, ligne 46), ajouter :
```makefile
# Sidecar ClamAV : image derivee avec clamd.conf aux limites relevees.
CLAMAV_IMAGE_URI := $(REGION)-docker.pkg.dev/$(PROJECT_ID)/$(REPO)/praxedo-upload-clamav:$(IMAGE_TAG)
CLAMAV_IMAGE     ?= $(CLAMAV_IMAGE_URI)
```

c) Ajouter `build-push-clamav` a la ligne `.PHONY` (ligne 53) :
```makefile
.PHONY: help config scanner-sa build-push build-push-clamav deploy iam url all
```

d) Ajouter la cible (apres la cible `build-push`, avant `deploy`) :
```makefile
build-push-clamav: ## Build+push the derived ClamAV sidecar image (raised size limits).
	docker buildx build --platform linux/amd64 --provenance=false \
	  -t $(CLAMAV_IMAGE_URI) --push $(DEPLOY_DIR)/clamav
```

e) Dans la cible `deploy`, sur la ligne du sidecar (ligne 110), remplacer `--memory=2Gi` par `--memory=4Gi` :
```makefile
	  --container=clamav --image=$(CLAMAV_IMAGE) --cpu=2 --memory=4Gi \
```

f) Dans la cible `all` (prerequis `scanner-sa build-push deploy iam`), inserer `build-push-clamav` avant `deploy` :
```makefile
all: scanner-sa build-push build-push-clamav deploy iam ## scanner-sa + build-push (x2) + deploy + iam.
```

- [ ] **Step 4: Verifier la coherence du Makefile (dry-run des variables)**

Run: `make -C praxedo-upload-scanner/deploy config`
Expected: la commande affiche la configuration sans erreur de syntaxe Make (les variables se resolvent). `CLAMAV_IMAGE` pointe desormais vers l'URI Artifact Registry `.../praxedo-upload-clamav:latest`.

- [ ] **Step 5: Mettre a jour le README du scanner**

Dans `praxedo-upload-scanner/README.md`, remplacer le bullet « Gros fichiers » de la section « Piege connu » par :

```markdown
- **Gros fichiers** : clamd limite la taille d'un flux INSTREAM (`StreamMaxLength`) et le
  scan (`MaxFileSize` / `MaxScanSize`). Le defaut de l'image officielle est 25M ; le sidecar
  utilise ici une **image derivee** (`deploy/clamav/Dockerfile`) qui releve ces trois valeurs
  a **1200M** (~1,2 Go), pour supporter le plafond applicatif de 1 Go. Pour aller au-dela :
  relever ces valeurs dans le Dockerfile derive **et** la RAM du sidecar `clamav`
  (`--memory` dans `deploy/Makefile`, 4Gi actuellement).
```

- [ ] **Step 6: Commit**

```bash
git add praxedo-upload-scanner/deploy/clamav/Dockerfile \
        praxedo-upload-scanner/deploy/Makefile \
        praxedo-upload-scanner/README.md
git commit -m "feat(scanner): raise ClamAV size limits via derived image, 4Gi sidecar"
```

---

## Task 3: Front — affichage de la limite + pre-validation

**Files:**
- Modify: `praxedo-upload-ui/src/config.ts`
- Modify: `praxedo-upload-ui/src/components/UploadModal.tsx`

Le projet n'a **pas** de tests front (retires intentionnellement) ; verification par `typecheck` + `build` + controle visuel.

- [ ] **Step 1: Ajouter la constante MAX_UPLOAD_BYTES**

A la fin de `praxedo-upload-ui/src/config.ts`, ajouter :

```typescript
// Plafond d'upload en octets. DOIT rester aligne avec storage.max-upload-size du backend
// (Spring DataSize "1GB" = 1 073 741 824 octets). Affiche "1 Go" cote UI.
export const MAX_UPLOAD_BYTES = 1024 * 1024 * 1024;
```

- [ ] **Step 2: Ajouter l'etat de rejet + la pre-validation dans UploadModal**

Dans `praxedo-upload-ui/src/components/UploadModal.tsx` :

a) Ajouter l'import (apres l'import de `hooks`) :
```typescript
import { MAX_UPLOAD_BYTES } from '../config';
```

b) Ajouter un etat, sous `const [dragOver, setDragOver] = useState(false);` :
```typescript
  const [rejected, setRejected] = useState<string[]>([]);
```

c) Remplacer la fonction `handleFiles` (lignes 14-23) par :
```typescript
  const handleFiles = (list: FileList | null) => {
    if (!list || list.length === 0) return;
    const all = Array.from(list);
    const tooBig = all.filter((f) => f.size > MAX_UPLOAD_BYTES);
    const ok = all.filter((f) => f.size <= MAX_UPLOAD_BYTES);
    setRejected(tooBig.map((f) => f.name));
    if (ok.length === 0) return;
    upload.mutate(ok, {
      onSuccess: (res) => {
        onUploaded(res);
        if (res.errors.length === 0 && tooBig.length === 0) onClose();
      },
    });
  };
```

d) Mettre a jour le libelle (ligne ~160). Remplacer :
```tsx
              Tous formats · jusqu'à 2 Go par fichier · sélection multiple
```
par :
```tsx
              Tous formats · jusqu'à 1 Go par fichier · sélection multiple
```

e) Ajouter un bandeau de rejet juste avant le bloc `{failed.length > 0 && (` :
```tsx
          {rejected.length > 0 && (
            <div
              role="alert"
              style={{
                marginTop: 16,
                background: '#FBEBE9',
                color: '#B23B30',
                borderRadius: 10,
                padding: '10px 14px',
                fontSize: 12.5,
                fontWeight: 600,
                lineHeight: 1.5,
              }}
            >
              {rejected.length} fichier(s) trop volumineux (max 1 Go) : {rejected.join(', ')}
            </div>
          )}
```

- [ ] **Step 3: Type-check + build**

Run: `pnpm -C praxedo-upload-ui typecheck && pnpm -C praxedo-upload-ui build`
Expected: aucun erreur TypeScript, build Vite reussi.

- [ ] **Step 4: Verification visuelle manuelle**

Run: `pnpm -C praxedo-upload-ui dev` puis ouvrir la modale d'upload.
Expected: le pied de la zone de depot affiche « jusqu'a 1 Go par fichier ». (Optionnel : abaisser temporairement `MAX_UPLOAD_BYTES` a `1000`, deposer un fichier plus gros, verifier le bandeau rouge « trop volumineux », puis retablir la valeur.)

- [ ] **Step 5: Commit**

```bash
git add praxedo-upload-ui/src/config.ts praxedo-upload-ui/src/components/UploadModal.tsx
git commit -m "feat(ui): display 1GB upload limit and pre-validate file size"
```

---

## Task 4: Documentation — README racine

**Files:**
- Modify: `README.md` (racine)

Pas de test (documentation).

- [ ] **Step 1: Documenter la limite dans « Choix techniques & hypotheses »**

Dans `README.md`, section `## Choix techniques & hypothèses (transverses)`, ajouter ce bullet apres le bullet « URLs signées direct-to-GCS » :

```markdown
- **Plafond d'upload : 1 Go** (`storage.max-upload-size`, surchargeable via `STORAGE_MAX_UPLOAD_SIZE`).
  L'API rejette (413) toute taille declaree superieure ; le front pre-valide et l'affiche. La limite
  est dictee par la **capacite du service de scan** (ClamAV) — le worker ne bufferise jamais le fichier
  (il ne transmet que l'URI GCS), la contrainte est la directive `StreamMaxLength` de clamd et la RAM
  du sidecar ClamAV (4 Gio). Pour augmenter la limite : relever conjointement `storage.max-upload-size`,
  les directives `StreamMaxLength`/`MaxFileSize`/`MaxScanSize` (image `deploy/clamav/Dockerfile` du
  scanner), la RAM du sidecar `clamav`, et la constante `MAX_UPLOAD_BYTES` du front.
```

- [ ] **Step 2: Ajouter la piste d'amelioration (enforcement dur)**

Dans `README.md`, section `## Pistes d'amélioration`, ajouter :

```markdown
- Enforcement dur du plafond au niveau du stockage : URL signee GCS bornee
  (`X-Goog-Content-Length-Range`) pour refuser cote GCS un depassement de la taille declaree ;
  exposer la limite via un endpoint `/config` pour une source unique cote front.
```

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: document 1GB upload limit and hard-enforcement follow-up"
```

---

## Verification finale

- [ ] **Backend** : `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn -f praxedo-upload-backend/pom.xml test` → PASS.
- [ ] **Front** : `pnpm -C praxedo-upload-ui typecheck && pnpm -C praxedo-upload-ui build` → OK.
- [ ] **Scanner** : image derivee buildee, `clamd.conf` effectif verifie (Task 2, Step 2).
- [ ] **E2e (optionnel, deploye)** : via la skill `testing-praxedo-e2e` — upload d'un fichier proche de 1 Go => `CLEAN` ; upload > 1 Go => 413 API + rejet UI. A executer apres deploiement du scanner (image ClamAV derivee) et du backend.
- [ ] **PR** : ouvrir une PR `task/upload-size-limit` vers `main` (une PR par tache, cf. CLAUDE.md).

---

## Self-review (rempli lors de l'ecriture)

- **Couverture spec** : plafond configurable (T1), 413 (T1), scanner StreamMaxLength/MaxFileSize/MaxScanSize + RAM 4Gi (T2), front affichage + pre-validation (T3), README racine + justification capacite scan (T4), README scanner (T2). Enforcement dur et endpoint /config = hors perimetre, documentes en piste (T4). ✔
- **Coherence des types** : `FileTooLargeException(long, long)` utilisee identiquement dans le service, le handler et les tests ; `MAX_UPLOAD_BYTES` (number) unique cote front ; `storage.max-upload-size` (DataSize) unique cote back. ✔
- **Pas de placeholder** : chaque etape porte le code/commande complet. Seul point conditionnel assume : le fallback « clamd.conf complet » si l'image de base genere sa conf au runtime (Task 2, Step 2) — verifie empiriquement par le build. ✔
