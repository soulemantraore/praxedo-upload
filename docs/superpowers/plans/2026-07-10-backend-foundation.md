# Backend — Fondation (domaine + API, testable en local) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Construire le cœur du micro-service `praxedo-upload-backend` (domaine, cas d'usage, API REST, auth par clé API par-client, batch, flux de scan) qui tourne et se teste **entièrement en local, sans dépendance GCP**, grâce à une architecture hexagonale et des adapters in-memory/fake.

**Architecture:** Hexagonale allégée. Le `domain` (POJO purs, sans annotation framework) ne dépend que de **ports (interfaces)**. Les `application` services orchestrent les cas d'usage. Les adapters `infrastructure` (in-memory, local filesystem, fake scanner, web REST) sont injectés par Spring **par constructeur**. Ce jalon utilise des adapters locaux ; les jalons suivants ajoutent ClamAV/Pub/Sub (jalon 2) puis GCS/Cloud SQL (jalon 3) **sans toucher au domaine**.

**Tech Stack:** Java 21, Spring Boot 3.3.x, Maven, JUnit 5 + AssertJ + Mockito (parcimonie), MockMvc pour les tests web. Pas de base externe dans ce jalon (repos in-memory).

---

## Principes appliqués (rappels — voir spec §6.1)

- **Injection par constructeur partout** ; jamais de `@Autowired` sur champ.
- **Domaine POJO** : aucune annotation Spring/JPA dans `domain`.
- **`Clock` et `IdGenerator` injectés** ; jamais `Instant.now()` / `UUID.randomUUID()` en dur.
- **Adapters in-memory = doubles de test** (réutilisés en dev local).
- **Value objects immuables** (records Java 21).
- TDD strict, commits fréquents.

## Invariant à garantir (spec §2)

> Un fichier n'est téléchargeable **que si son statut est `CLEAN`**. Appliqué à un seul endroit : `FileDownloadService`.

## Machine à états (spec §5)

`PENDING → SCANNING → { CLEAN | INFECTED | SCAN_FAILED }` ; `PENDING → EXPIRED` (orphelin) ; `SCAN_FAILED → SCANNING` (retry). Toute autre transition est interdite.

---

## Carte des fichiers (package racine `com.praxedo.upload`)

```
domain/
  file/FileStatus.java            enum + transitions autorisées + isDownloadable()
  file/FileRecord.java            entité POJO (état + transitions)
  file/ScanVerdict.java           record (CLEAN/INFECTED + engine + threatName + scannedAt)
  file/StatusCounts.java          record (total, clean, scanning, pending, blocked)
  file/FileQuery.java             record (ownerId, q, status, page, size)
  file/PageResult.java            record générique (items, page, totalPages, totalElements)
  file/exceptions/...             FileNotFoundException, IllegalFileTransitionException, DownloadNotAllowedException
  client/ApiClient.java           record (id, name, apiKeyHash, active, createdAt)
  port/FileMetadataRepository.java
  port/FileStorage.java           + UploadTarget (record)
  port/AntivirusScanner.java      + ScanException
  port/ScanQueue.java             + ScanRequest (record)
  port/ApiClientRepository.java
  port/IdGenerator.java
application/
  FileUploadService.java          registerUpload / registerBatch
  FileScanService.java            scan(fileId) : flux SCANNING → verdict
  FileQueryService.java           list / stats / getFile / getBatch (owner-scopé)
  FileDownloadService.java        requestDownload (gate CLEAN)
  ReconciliationService.java      expireStalePending / requeueStuckScanning
  ApiKeyService.java              hashage + résolution owner + création client
  dto/...                         commandes & résultats (records)
infrastructure/
  persistence/inmemory/InMemoryFileMetadataRepository.java
  persistence/inmemory/InMemoryApiClientRepository.java
  storage/local/LocalFileStorage.java
  scan/InProcessScanQueue.java
  scan/FakeAntivirusScanner.java  (détecte la signature EICAR → INFECTED)
  id/UuidIdGenerator.java
  web/FilesController.java
  web/BatchesController.java
  web/LocalStorageController.java (proxy upload/download en profil local)
  web/ApiKeyAuthFilter.java
  web/GlobalExceptionHandler.java
  web/dto/...                     DTO HTTP (records)
  config/StorageProperties.java   @ConfigurationProperties
  config/SecurityConfig.java
UploadBackendApplication.java
```

---

## Task 1: Scaffold du projet Maven + Spring Boot

**Files:**
- Create: `praxedo-upload-backend/pom.xml`
- Create: `praxedo-upload-backend/src/main/java/com/praxedo/upload/UploadBackendApplication.java`
- Create: `praxedo-upload-backend/src/main/resources/application.yml`
- Create: `praxedo-upload-backend/.gitignore`
- Test: `praxedo-upload-backend/src/test/java/com/praxedo/upload/UploadBackendApplicationTests.java`

- [ ] **Step 1: Créer le `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.5</version>
    <relativePath/>
  </parent>
  <groupId>com.praxedo</groupId>
  <artifactId>praxedo-upload-backend</artifactId>
  <version>0.1.0</version>
  <properties>
    <java.version>21</java.version>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.security</groupId>
      <artifactId>spring-security-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 2: Créer la classe application et la config**

`UploadBackendApplication.java` :
```java
package com.praxedo.upload;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UploadBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(UploadBackendApplication.class, args);
    }
}
```

`application.yml` :
```yaml
spring:
  application:
    name: praxedo-upload-backend
storage:
  local:
    base-dir: ${STORAGE_LOCAL_DIR:./data/uploads}
  upload-url-ttl: PT15M
  download-url-ttl: PT5M
```

`.gitignore` :
```
target/
data/
*.log
.DS_Store
.idea/
*.iml
```

- [ ] **Step 3: Écrire le test de chargement du contexte**

```java
package com.praxedo.upload;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class UploadBackendApplicationTests {
    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 4: Lancer le test**

Run: `cd praxedo-upload-backend && ./mvnw test -Dtest=UploadBackendApplicationTests`
Expected: le contexte se charge, BUILD SUCCESS. (Si Spring Security bloque tout, c'est normal à ce stade ; le test de contexte passe quand même.)

- [ ] **Step 5: Init git + commit**

```bash
cd praxedo-upload-backend
git init
mvn -N io.takari:maven:wrapper || true   # optionnel : générer mvnw si absent
git add .
git commit -m "chore: scaffold Spring Boot 3.3 / Java 21 backend"
```

---

## Task 2: `FileStatus` — enum, transitions, downloadable

**Files:**
- Create: `.../domain/file/FileStatus.java`
- Test: `.../domain/file/FileStatusTest.java`

- [ ] **Step 1: Écrire le test qui échoue**

```java
package com.praxedo.upload.domain.file;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FileStatusTest {
    @Test
    void pending_can_go_to_scanning_or_expired() {
        assertThat(FileStatus.PENDING.canTransitionTo(FileStatus.SCANNING)).isTrue();
        assertThat(FileStatus.PENDING.canTransitionTo(FileStatus.EXPIRED)).isTrue();
        assertThat(FileStatus.PENDING.canTransitionTo(FileStatus.CLEAN)).isFalse();
    }

    @Test
    void scanning_can_reach_verdicts() {
        assertThat(FileStatus.SCANNING.canTransitionTo(FileStatus.CLEAN)).isTrue();
        assertThat(FileStatus.SCANNING.canTransitionTo(FileStatus.INFECTED)).isTrue();
        assertThat(FileStatus.SCANNING.canTransitionTo(FileStatus.SCAN_FAILED)).isTrue();
    }

    @Test
    void scan_failed_can_be_retried() {
        assertThat(FileStatus.SCAN_FAILED.canTransitionTo(FileStatus.SCANNING)).isTrue();
    }

    @Test
    void terminal_states_are_final() {
        assertThat(FileStatus.CLEAN.canTransitionTo(FileStatus.SCANNING)).isFalse();
        assertThat(FileStatus.INFECTED.canTransitionTo(FileStatus.CLEAN)).isFalse();
    }

    @Test
    void only_clean_is_downloadable() {
        assertThat(FileStatus.CLEAN.isDownloadable()).isTrue();
        assertThat(FileStatus.PENDING.isDownloadable()).isFalse();
        assertThat(FileStatus.INFECTED.isDownloadable()).isFalse();
    }
}
```

- [ ] **Step 2: Lancer le test → échec (classe absente)**

Run: `./mvnw test -Dtest=FileStatusTest`
Expected: FAIL (compilation : `FileStatus` introuvable).

- [ ] **Step 3: Implémenter `FileStatus`**

```java
package com.praxedo.upload.domain.file;

import java.util.Set;

public enum FileStatus {
    PENDING,
    SCANNING,
    CLEAN,
    INFECTED,
    SCAN_FAILED,
    EXPIRED;

    private static final java.util.Map<FileStatus, Set<FileStatus>> ALLOWED = java.util.Map.of(
        PENDING, Set.of(SCANNING, EXPIRED),
        SCANNING, Set.of(CLEAN, INFECTED, SCAN_FAILED),
        SCAN_FAILED, Set.of(SCANNING),
        CLEAN, Set.of(),
        INFECTED, Set.of(),
        EXPIRED, Set.of()
    );

    public boolean canTransitionTo(FileStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isDownloadable() {
        return this == CLEAN;
    }
}
```

- [ ] **Step 4: Lancer le test → succès**

Run: `./mvnw test -Dtest=FileStatusTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/praxedo/upload/domain/file/FileStatus.java src/test/java/com/praxedo/upload/domain/file/FileStatusTest.java
git commit -m "feat(domain): FileStatus with allowed transitions and downloadable rule"
```

---

## Task 3: `ScanVerdict` (value object)

**Files:**
- Create: `.../domain/file/ScanVerdict.java`
- Test: `.../domain/file/ScanVerdictTest.java`

- [ ] **Step 1: Test**

```java
package com.praxedo.upload.domain.file;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class ScanVerdictTest {
    @Test
    void clean_verdict_has_no_threat() {
        ScanVerdict v = ScanVerdict.clean("clamav", Instant.parse("2026-07-10T10:00:00Z"));
        assertThat(v.infected()).isFalse();
        assertThat(v.threatName()).isNull();
    }

    @Test
    void infected_verdict_carries_threat_name() {
        ScanVerdict v = ScanVerdict.infected("clamav", "Eicar-Test-Signature", Instant.parse("2026-07-10T10:00:00Z"));
        assertThat(v.infected()).isTrue();
        assertThat(v.threatName()).isEqualTo("Eicar-Test-Signature");
    }
}
```

- [ ] **Step 2: Lancer → échec**

Run: `./mvnw test -Dtest=ScanVerdictTest` — Expected: FAIL (classe absente).

- [ ] **Step 3: Implémenter**

```java
package com.praxedo.upload.domain.file;

import java.time.Instant;
import java.util.Objects;

public record ScanVerdict(boolean infected, String engine, String threatName, Instant scannedAt) {
    public ScanVerdict {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(scannedAt, "scannedAt");
    }
    public static ScanVerdict clean(String engine, Instant scannedAt) {
        return new ScanVerdict(false, engine, null, scannedAt);
    }
    public static ScanVerdict infected(String engine, String threatName, Instant scannedAt) {
        return new ScanVerdict(true, engine, Objects.requireNonNull(threatName, "threatName"), scannedAt);
    }
}
```

- [ ] **Step 4: Lancer → succès**

Run: `./mvnw test -Dtest=ScanVerdictTest` — Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/praxedo/upload/domain/file/ScanVerdict.java src/test/java/com/praxedo/upload/domain/file/ScanVerdictTest.java
git commit -m "feat(domain): ScanVerdict value object"
```

---

## Task 4: `FileRecord` — entité, transitions, invariant

**Files:**
- Create: `.../domain/file/FileRecord.java`
- Create: `.../domain/file/exceptions/IllegalFileTransitionException.java`
- Test: `.../domain/file/FileRecordTest.java`

- [ ] **Step 1: Test**

```java
package com.praxedo.upload.domain.file;

import com.praxedo.upload.domain.file.exceptions.IllegalFileTransitionException;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class FileRecordTest {
    private static final Instant T0 = Instant.parse("2026-07-10T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-07-10T10:01:00Z");

    private FileRecord pending() {
        return FileRecord.pending(
            UUID.randomUUID(), UUID.randomUUID(), null,
            "rapport.pdf", "application/pdf", 1024L, "owner/key.pdf", T0);
    }

    @Test
    void new_record_is_pending_and_not_downloadable() {
        FileRecord f = pending();
        assertThat(f.status()).isEqualTo(FileStatus.PENDING);
        assertThat(f.isDownloadable()).isFalse();
    }

    @Test
    void scanning_then_clean_makes_it_downloadable() {
        FileRecord f = pending();
        f.markScanning(T1);
        f.markClean(ScanVerdict.clean("clamav", T1), T1);
        assertThat(f.status()).isEqualTo(FileStatus.CLEAN);
        assertThat(f.isDownloadable()).isTrue();
        assertThat(f.scanVerdict().infected()).isFalse();
    }

    @Test
    void infected_keeps_verdict_and_is_not_downloadable() {
        FileRecord f = pending();
        f.markScanning(T1);
        f.markInfected(ScanVerdict.infected("clamav", "Eicar", T1), T1);
        assertThat(f.status()).isEqualTo(FileStatus.INFECTED);
        assertThat(f.isDownloadable()).isFalse();
    }

    @Test
    void illegal_transition_is_rejected() {
        FileRecord f = pending();
        assertThatThrownBy(() -> f.markClean(ScanVerdict.clean("clamav", T1), T1))
            .isInstanceOf(IllegalFileTransitionException.class);
    }

    @Test
    void scan_failed_can_be_requeued() {
        FileRecord f = pending();
        f.markScanning(T1);
        f.markScanFailed(T1);
        assertThat(f.status()).isEqualTo(FileStatus.SCAN_FAILED);
        assertThat(f.scanAttempts()).isEqualTo(1);
        f.markScanning(T1);
        assertThat(f.status()).isEqualTo(FileStatus.SCANNING);
    }
}
```

- [ ] **Step 2: Lancer → échec**

Run: `./mvnw test -Dtest=FileRecordTest` — Expected: FAIL (classes absentes).

- [ ] **Step 3: Implémenter l'exception puis l'entité**

`IllegalFileTransitionException.java` :
```java
package com.praxedo.upload.domain.file.exceptions;

import com.praxedo.upload.domain.file.FileStatus;

public class IllegalFileTransitionException extends RuntimeException {
    public IllegalFileTransitionException(FileStatus from, FileStatus to) {
        super("Transition interdite : " + from + " -> " + to);
    }
}
```

`FileRecord.java` :
```java
package com.praxedo.upload.domain.file;

import com.praxedo.upload.domain.file.exceptions.IllegalFileTransitionException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class FileRecord {
    private final UUID id;
    private final UUID ownerId;
    private final UUID batchId; // nullable
    private final String filename;
    private final String contentType;
    private final long sizeBytes;
    private final String storageKey;
    private FileStatus status;
    private ScanVerdict scanVerdict; // nullable
    private int scanAttempts;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant scannedAt; // nullable

    private FileRecord(UUID id, UUID ownerId, UUID batchId, String filename, String contentType,
                       long sizeBytes, String storageKey, FileStatus status, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.batchId = batchId;
        this.filename = Objects.requireNonNull(filename);
        this.contentType = Objects.requireNonNull(contentType);
        this.sizeBytes = sizeBytes;
        this.storageKey = Objects.requireNonNull(storageKey);
        this.status = Objects.requireNonNull(status);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = createdAt;
    }

    public static FileRecord pending(UUID id, UUID ownerId, UUID batchId, String filename,
                                     String contentType, long sizeBytes, String storageKey, Instant now) {
        return new FileRecord(id, ownerId, batchId, filename, contentType, sizeBytes, storageKey,
            FileStatus.PENDING, now);
    }

    /** Reconstruction depuis la persistance (jalon 3). */
    public static FileRecord rehydrate(UUID id, UUID ownerId, UUID batchId, String filename,
                                        String contentType, long sizeBytes, String storageKey,
                                        FileStatus status, ScanVerdict scanVerdict, int scanAttempts,
                                        Instant createdAt, Instant updatedAt, Instant scannedAt) {
        FileRecord f = new FileRecord(id, ownerId, batchId, filename, contentType, sizeBytes, storageKey, status, createdAt);
        f.scanVerdict = scanVerdict;
        f.scanAttempts = scanAttempts;
        f.updatedAt = updatedAt;
        f.scannedAt = scannedAt;
        return f;
    }

    private void transitionTo(FileStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalFileTransitionException(status, target);
        }
        this.status = target;
        this.updatedAt = now;
    }

    public void markScanning(Instant now) {
        transitionTo(FileStatus.SCANNING, now);
        this.scanAttempts++;
    }

    public void markClean(ScanVerdict verdict, Instant now) {
        transitionTo(FileStatus.CLEAN, now);
        this.scanVerdict = verdict;
        this.scannedAt = now;
    }

    public void markInfected(ScanVerdict verdict, Instant now) {
        transitionTo(FileStatus.INFECTED, now);
        this.scanVerdict = verdict;
        this.scannedAt = now;
    }

    public void markScanFailed(Instant now) {
        transitionTo(FileStatus.SCAN_FAILED, now);
    }

    public void markExpired(Instant now) {
        transitionTo(FileStatus.EXPIRED, now);
    }

    public boolean isDownloadable() { return status.isDownloadable(); }

    public UUID id() { return id; }
    public UUID ownerId() { return ownerId; }
    public UUID batchId() { return batchId; }
    public String filename() { return filename; }
    public String contentType() { return contentType; }
    public long sizeBytes() { return sizeBytes; }
    public String storageKey() { return storageKey; }
    public FileStatus status() { return status; }
    public ScanVerdict scanVerdict() { return scanVerdict; }
    public int scanAttempts() { return scanAttempts; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant scannedAt() { return scannedAt; }
}
```

- [ ] **Step 4: Lancer → succès**

Run: `./mvnw test -Dtest=FileRecordTest` — Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/praxedo/upload/domain/file/FileRecord.java \
        src/main/java/com/praxedo/upload/domain/file/exceptions/IllegalFileTransitionException.java \
        src/test/java/com/praxedo/upload/domain/file/FileRecordTest.java
git commit -m "feat(domain): FileRecord entity enforcing state machine"
```

---

## Task 5: Value objects de requête + `ApiClient` + exceptions restantes

**Files:**
- Create: `.../domain/file/FileQuery.java`, `PageResult.java`, `StatusCounts.java`
- Create: `.../domain/file/exceptions/FileNotFoundException.java`, `DownloadNotAllowedException.java`
- Create: `.../domain/client/ApiClient.java`
- Test: `.../domain/file/DomainValueObjectsTest.java`

- [ ] **Step 1: Test**

```java
package com.praxedo.upload.domain.file;

import com.praxedo.upload.domain.client.ApiClient;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class DomainValueObjectsTest {
    @Test
    void page_result_computes_total_pages() {
        PageResult<String> p = PageResult.of(List.of("a", "b"), 0, 10, 25);
        assertThat(p.totalPages()).isEqualTo(3);
        assertThat(p.items()).containsExactly("a", "b");
    }

    @Test
    void file_query_defaults_are_sane() {
        FileQuery q = FileQuery.of(UUID.randomUUID(), null, null, 0, 20);
        assertThat(q.size()).isEqualTo(20);
    }

    @Test
    void api_client_is_active_by_construction() {
        ApiClient c = new ApiClient(UUID.randomUUID(), "Bâtir SA", "hash", true, Instant.now());
        assertThat(c.active()).isTrue();
    }
}
```

- [ ] **Step 2: Lancer → échec**

Run: `./mvnw test -Dtest=DomainValueObjectsTest` — Expected: FAIL.

- [ ] **Step 3: Implémenter**

`PageResult.java` :
```java
package com.praxedo.upload.domain.file;

import java.util.List;

public record PageResult<T>(List<T> items, int page, int size, long totalElements) {
    public static <T> PageResult<T> of(List<T> items, int page, int size, long totalElements) {
        return new PageResult<>(List.copyOf(items), page, size, totalElements);
    }
    public int totalPages() {
        return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }
}
```

`FileQuery.java` :
```java
package com.praxedo.upload.domain.file;

import java.util.UUID;

public record FileQuery(UUID ownerId, String q, FileStatus status, int page, int size) {
    public static FileQuery of(UUID ownerId, String q, FileStatus status, int page, int size) {
        return new FileQuery(ownerId, q, status, Math.max(0, page), size <= 0 ? 20 : Math.min(size, 100));
    }
}
```

`StatusCounts.java` :
```java
package com.praxedo.upload.domain.file;

public record StatusCounts(long total, long clean, long scanning, long pending, long blocked) {}
```

`FileNotFoundException.java` :
```java
package com.praxedo.upload.domain.file.exceptions;

import java.util.UUID;

public class FileNotFoundException extends RuntimeException {
    public FileNotFoundException(UUID id) { super("Fichier introuvable : " + id); }
}
```

`DownloadNotAllowedException.java` :
```java
package com.praxedo.upload.domain.file.exceptions;

import com.praxedo.upload.domain.file.FileStatus;

public class DownloadNotAllowedException extends RuntimeException {
    public DownloadNotAllowedException(FileStatus status) {
        super("Téléchargement refusé : statut " + status + " (seul CLEAN est téléchargeable)");
    }
}
```

`ApiClient.java` :
```java
package com.praxedo.upload.domain.client;

import java.time.Instant;
import java.util.UUID;

public record ApiClient(UUID id, String name, String apiKeyHash, boolean active, Instant createdAt) {}
```

- [ ] **Step 4: Lancer → succès**

Run: `./mvnw test -Dtest=DomainValueObjectsTest` — Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/praxedo/upload/domain/ src/test/java/com/praxedo/upload/domain/file/DomainValueObjectsTest.java
git commit -m "feat(domain): query/paging value objects, ApiClient, domain exceptions"
```

---

## Task 6: Les ports (interfaces)

**Files:**
- Create: `.../domain/port/FileMetadataRepository.java`, `FileStorage.java` (+ `UploadTarget`), `AntivirusScanner.java` (+ `ScanException`), `ScanQueue.java` (+ `ScanRequest`), `ApiClientRepository.java`, `IdGenerator.java`

_Interfaces sans logique → pas de test unitaire dédié ; elles sont exercées par les tests des adapters et services._

- [ ] **Step 1: Créer les interfaces**

`IdGenerator.java` :
```java
package com.praxedo.upload.domain.port;

import java.util.UUID;

public interface IdGenerator {
    UUID newId();
}
```

`FileStorage.java` :
```java
package com.praxedo.upload.domain.port;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;

public interface FileStorage {
    record UploadTarget(URI url, Instant expiresAt) {}

    /** URL/endpoint où le client PUT les octets (signée en GCS, proxy en local). */
    UploadTarget createUploadTarget(String storageKey, String contentType, long sizeBytes);

    /** URL de téléchargement (signée en GCS, proxy en local). */
    URI createDownloadUrl(String storageKey, Duration ttl);

    /** Lecture des octets (utilisée par le worker de scan). */
    InputStream read(String storageKey);

    void delete(String storageKey);

    boolean exists(String storageKey);
}
```

`AntivirusScanner.java` :
```java
package com.praxedo.upload.domain.port;

import com.praxedo.upload.domain.file.ScanVerdict;
import java.io.InputStream;
import java.time.Instant;

public interface AntivirusScanner {
    /** @throws ScanException en cas d'échec technique (≠ menace). */
    ScanVerdict scan(InputStream content, Instant scannedAt) throws ScanException;

    class ScanException extends Exception {
        public ScanException(String message, Throwable cause) { super(message, cause); }
        public ScanException(String message) { super(message); }
    }
}
```

`ScanQueue.java` :
```java
package com.praxedo.upload.domain.port;

import java.util.UUID;

public interface ScanQueue {
    record ScanRequest(UUID fileId) {}
    void enqueue(ScanRequest request);
}
```

`FileMetadataRepository.java` :
```java
package com.praxedo.upload.domain.port;

import com.praxedo.upload.domain.file.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileMetadataRepository {
    void save(FileRecord file);
    Optional<FileRecord> findById(UUID id);                       // interne (worker)
    Optional<FileRecord> findByIdAndOwner(UUID id, UUID ownerId); // owner-scopé
    PageResult<FileRecord> search(FileQuery query);
    List<FileRecord> findByBatchAndOwner(UUID batchId, UUID ownerId);
    StatusCounts countByOwner(UUID ownerId);
    List<FileRecord> findByStatusOlderThan(FileStatus status, Instant threshold);
}
```

`ApiClientRepository.java` :
```java
package com.praxedo.upload.domain.port;

import com.praxedo.upload.domain.client.ApiClient;
import java.util.Optional;

public interface ApiClientRepository {
    Optional<ApiClient> findByApiKeyHash(String apiKeyHash);
    void save(ApiClient client);
}
```

- [ ] **Step 2: Compiler**

Run: `./mvnw compile` — Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/praxedo/upload/domain/port/
git commit -m "feat(domain): ports (repository, storage, scanner, queue, id generator)"
```

---

## Task 7: Adapters in-memory + `UuidIdGenerator`

**Files:**
- Create: `.../infrastructure/persistence/inmemory/InMemoryFileMetadataRepository.java`, `InMemoryApiClientRepository.java`
- Create: `.../infrastructure/id/UuidIdGenerator.java`
- Test: `.../infrastructure/persistence/inmemory/InMemoryFileMetadataRepositoryTest.java`

- [ ] **Step 1: Test du repository in-memory**

```java
package com.praxedo.upload.infrastructure.persistence.inmemory;

import com.praxedo.upload.domain.file.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class InMemoryFileMetadataRepositoryTest {
    private final InMemoryFileMetadataRepository repo = new InMemoryFileMetadataRepository();
    private final UUID owner = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-07-10T10:00:00Z");

    private FileRecord file(String name, FileStatus status) {
        FileRecord f = FileRecord.pending(UUID.randomUUID(), owner, null, name, "text/plain", 10, "k/" + name, now);
        if (status == FileStatus.SCANNING || status == FileStatus.CLEAN) {
            f.markScanning(now);
        }
        if (status == FileStatus.CLEAN) f.markClean(ScanVerdict.clean("fake", now), now);
        return f;
    }

    @Test
    void save_then_find_by_owner() {
        FileRecord f = file("a.txt", FileStatus.PENDING);
        repo.save(f);
        assertThat(repo.findByIdAndOwner(f.id(), owner)).isPresent();
        assertThat(repo.findByIdAndOwner(f.id(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void search_filters_by_owner_and_status_and_query() {
        repo.save(file("rapport.txt", FileStatus.CLEAN));
        repo.save(file("photo.txt", FileStatus.PENDING));
        FileQuery q = FileQuery.of(owner, "rap", null, 0, 20);
        PageResult<FileRecord> page = repo.search(q);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).filename()).isEqualTo("rapport.txt");
    }

    @Test
    void count_by_owner_groups_statuses() {
        repo.save(file("a.txt", FileStatus.CLEAN));
        repo.save(file("b.txt", FileStatus.PENDING));
        StatusCounts c = repo.countByOwner(owner);
        assertThat(c.total()).isEqualTo(2);
        assertThat(c.clean()).isEqualTo(1);
        assertThat(c.pending()).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Lancer → échec**

Run: `./mvnw test -Dtest=InMemoryFileMetadataRepositoryTest` — Expected: FAIL.

- [ ] **Step 3: Implémenter les adapters**

`UuidIdGenerator.java` :
```java
package com.praxedo.upload.infrastructure.id;

import com.praxedo.upload.domain.port.IdGenerator;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class UuidIdGenerator implements IdGenerator {
    @Override public UUID newId() { return UUID.randomUUID(); }
}
```

`InMemoryFileMetadataRepository.java` :
```java
package com.praxedo.upload.infrastructure.persistence.inmemory;

import com.praxedo.upload.domain.file.*;
import com.praxedo.upload.domain.port.FileMetadataRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Repository
@Profile({"local", "test"})
public class InMemoryFileMetadataRepository implements FileMetadataRepository {
    private final Map<UUID, FileRecord> store = new java.util.concurrent.ConcurrentHashMap<>();

    @Override public void save(FileRecord file) { store.put(file.id(), file); }

    @Override public Optional<FileRecord> findById(UUID id) { return Optional.ofNullable(store.get(id)); }

    @Override public Optional<FileRecord> findByIdAndOwner(UUID id, UUID ownerId) {
        return findById(id).filter(f -> f.ownerId().equals(ownerId));
    }

    @Override public PageResult<FileRecord> search(FileQuery query) {
        List<FileRecord> matched = store.values().stream()
            .filter(f -> f.ownerId().equals(query.ownerId()))
            .filter(f -> query.status() == null || f.status() == query.status())
            .filter(f -> query.q() == null || f.filename().toLowerCase().contains(query.q().toLowerCase()))
            .sorted(Comparator.comparing(FileRecord::createdAt).reversed())
            .collect(Collectors.toList());
        int from = Math.min(query.page() * query.size(), matched.size());
        int to = Math.min(from + query.size(), matched.size());
        return PageResult.of(matched.subList(from, to), query.page(), query.size(), matched.size());
    }

    @Override public List<FileRecord> findByBatchAndOwner(UUID batchId, UUID ownerId) {
        return store.values().stream()
            .filter(f -> ownerId.equals(f.ownerId()) && batchId.equals(f.batchId()))
            .sorted(Comparator.comparing(FileRecord::createdAt))
            .collect(Collectors.toList());
    }

    @Override public StatusCounts countByOwner(UUID ownerId) {
        List<FileRecord> owned = store.values().stream()
            .filter(f -> f.ownerId().equals(ownerId)).toList();
        long total = owned.size();
        long clean = owned.stream().filter(f -> f.status() == FileStatus.CLEAN).count();
        long scanning = owned.stream().filter(f -> f.status() == FileStatus.SCANNING).count();
        long pending = owned.stream().filter(f -> f.status() == FileStatus.PENDING).count();
        long blocked = owned.stream().filter(f -> f.status() == FileStatus.INFECTED).count();
        return new StatusCounts(total, clean, scanning, pending, blocked);
    }

    @Override public List<FileRecord> findByStatusOlderThan(FileStatus status, Instant threshold) {
        return store.values().stream()
            .filter(f -> f.status() == status && f.updatedAt().isBefore(threshold))
            .collect(Collectors.toList());
    }
}
```

`InMemoryApiClientRepository.java` :
```java
package com.praxedo.upload.infrastructure.persistence.inmemory;

import com.praxedo.upload.domain.client.ApiClient;
import com.praxedo.upload.domain.port.ApiClientRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
@Profile({"local", "test"})
public class InMemoryApiClientRepository implements ApiClientRepository {
    private final Map<String, ApiClient> byHash = new java.util.concurrent.ConcurrentHashMap<>();

    @Override public Optional<ApiClient> findByApiKeyHash(String apiKeyHash) {
        return Optional.ofNullable(byHash.get(apiKeyHash)).filter(ApiClient::active);
    }

    @Override public void save(ApiClient client) { byHash.put(client.apiKeyHash(), client); }
}
```

- [ ] **Step 4: Lancer → succès**

Run: `./mvnw test -Dtest=InMemoryFileMetadataRepositoryTest` — Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/praxedo/upload/infrastructure/persistence/inmemory/ \
        src/main/java/com/praxedo/upload/infrastructure/id/UuidIdGenerator.java \
        src/test/java/com/praxedo/upload/infrastructure/persistence/inmemory/
git commit -m "feat(infra): in-memory repositories and UUID id generator"
```

---

## Task 8: `LocalFileStorage` (filesystem + proxy URLs)

**Files:**
- Create: `.../infrastructure/config/StorageProperties.java`
- Create: `.../infrastructure/storage/local/LocalFileStorage.java`
- Test: `.../infrastructure/storage/local/LocalFileStorageTest.java`

- [ ] **Step 1: Test**

```java
package com.praxedo.upload.infrastructure.storage.local;

import com.praxedo.upload.domain.port.FileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

class LocalFileStorageTest {
    @Test
    void write_then_read_and_delete(@TempDir Path dir) throws Exception {
        LocalFileStorage storage = new LocalFileStorage(dir.toString(), "http://localhost:8080");
        storage.write("owner/a.txt", new ByteArrayInputStream("hello".getBytes()));
        assertThat(storage.exists("owner/a.txt")).isTrue();
        assertThat(new String(storage.read("owner/a.txt").readAllBytes())).isEqualTo("hello");
        storage.delete("owner/a.txt");
        assertThat(storage.exists("owner/a.txt")).isFalse();
    }

    @Test
    void upload_target_is_a_proxy_url() {
        LocalFileStorage storage = new LocalFileStorage("/tmp/x", "http://localhost:8080");
        FileStorage.UploadTarget t = storage.createUploadTarget("owner/a.txt", "text/plain", 5);
        assertThat(t.url().toString()).contains("/api/_local/upload");
        assertThat(storage.createDownloadUrl("owner/a.txt", Duration.ofMinutes(5)).toString())
            .contains("/api/_local/download");
    }
}
```

- [ ] **Step 2: Lancer → échec**

Run: `./mvnw test -Dtest=LocalFileStorageTest` — Expected: FAIL.

- [ ] **Step 3: Implémenter les properties puis le storage**

`StorageProperties.java` :
```java
package com.praxedo.upload.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "storage")
public record StorageProperties(Local local, Duration uploadUrlTtl, Duration downloadUrlTtl, String publicBaseUrl) {
    public record Local(String baseDir) {}
}
```

`LocalFileStorage.java` :
```java
package com.praxedo.upload.infrastructure.storage.local;

import com.praxedo.upload.domain.port.FileStorage;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;

@Component
@Profile({"local", "test"})
public class LocalFileStorage implements FileStorage {
    private final Path baseDir;
    private final String publicBaseUrl;

    public LocalFileStorage(
            @org.springframework.beans.factory.annotation.Value("${storage.local.base-dir}") String baseDir,
            @org.springframework.beans.factory.annotation.Value("${storage.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.baseDir = Path.of(baseDir);
        this.publicBaseUrl = publicBaseUrl;
    }

    private Path resolve(String key) { return baseDir.resolve(key).normalize(); }

    public void write(String key, InputStream content) {
        try {
            Path p = resolve(key);
            Files.createDirectories(p.getParent());
            Files.copy(content, p, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.io.IOException e) { throw new UncheckedIOException(e); }
    }

    @Override public InputStream read(String key) {
        try { return Files.newInputStream(resolve(key)); }
        catch (java.io.IOException e) { throw new UncheckedIOException(e); }
    }

    @Override public void delete(String key) {
        try { Files.deleteIfExists(resolve(key)); }
        catch (java.io.IOException e) { throw new UncheckedIOException(e); }
    }

    @Override public boolean exists(String key) { return Files.exists(resolve(key)); }

    @Override public UploadTarget createUploadTarget(String key, String contentType, long size) {
        return new UploadTarget(proxy("/api/_local/upload", key), Instant.now().plus(Duration.ofMinutes(15)));
    }

    @Override public URI createDownloadUrl(String key, Duration ttl) {
        return proxy("/api/_local/download", key);
    }

    private URI proxy(String path, String key) {
        String enc = URLEncoder.encode(key, StandardCharsets.UTF_8);
        return URI.create(publicBaseUrl + path + "?key=" + enc);
    }
}
```

> Note : ce jalon appelle `Instant.now()` uniquement pour l'expiration d'affichage du proxy local ; le domaine, lui, reçoit toujours un `Clock`/`Instant` injecté.

- [ ] **Step 4: Lancer → succès**

Run: `./mvnw test -Dtest=LocalFileStorageTest` — Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/praxedo/upload/infrastructure/storage/local/ \
        src/main/java/com/praxedo/upload/infrastructure/config/StorageProperties.java \
        src/test/java/com/praxedo/upload/infrastructure/storage/local/
git commit -m "feat(infra): LocalFileStorage with filesystem IO and proxy URLs"
```

---

## Task 9: `FakeAntivirusScanner` (détection EICAR)

> Les adapters de queue (`SynchronousScanQueue` en profil `test`, `InProcessScanQueue` `@Async` en profil `local`) dépendent de `FileScanService` (Task 11) ; ils sont donc créés en Task 11, pas ici.

**Files:**
- Create: `.../infrastructure/scan/FakeAntivirusScanner.java`
- Test: `.../infrastructure/scan/FakeAntivirusScannerTest.java`

- [ ] **Step 1: Test du scanner fake**

```java
package com.praxedo.upload.infrastructure.scan;

import com.praxedo.upload.domain.file.ScanVerdict;
import com.praxedo.upload.domain.port.AntivirusScanner;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class FakeAntivirusScannerTest {
    private final FakeAntivirusScanner scanner = new FakeAntivirusScanner();
    private final Instant now = Instant.parse("2026-07-10T10:00:00Z");

    // Signature de test standard EICAR (inoffensive)
    private static final String EICAR =
        "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

    @Test
    void clean_content_is_clean() throws Exception {
        ScanVerdict v = scanner.scan(new ByteArrayInputStream("bonjour".getBytes()), now);
        assertThat(v.infected()).isFalse();
    }

    @Test
    void eicar_content_is_infected() throws Exception {
        ScanVerdict v = scanner.scan(new ByteArrayInputStream(EICAR.getBytes()), now);
        assertThat(v.infected()).isTrue();
        assertThat(v.threatName()).contains("Eicar");
    }
}
```

- [ ] **Step 2: Lancer → échec**

Run: `./mvnw test -Dtest=FakeAntivirusScannerTest` — Expected: FAIL.

- [ ] **Step 3: Implémenter**

`FakeAntivirusScanner.java` :
```java
package com.praxedo.upload.infrastructure.scan;

import com.praxedo.upload.domain.file.ScanVerdict;
import com.praxedo.upload.domain.port.AntivirusScanner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** Doublure locale : marque INFECTED tout contenu portant la signature de test EICAR. */
@Component
@Profile({"local", "test"})
public class FakeAntivirusScanner implements AntivirusScanner {
    private static final String EICAR_MARKER = "EICAR-STANDARD-ANTIVIRUS-TEST-FILE";
    static final String ENGINE = "fake";

    @Override public ScanVerdict scan(InputStream content, Instant scannedAt) throws ScanException {
        try {
            String body = new String(content.readAllBytes(), StandardCharsets.UTF_8);
            if (body.contains(EICAR_MARKER)) {
                return ScanVerdict.infected(ENGINE, "Eicar-Test-Signature", scannedAt);
            }
            return ScanVerdict.clean(ENGINE, scannedAt);
        } catch (java.io.IOException e) {
            throw new ScanException("lecture du contenu impossible", e);
        }
    }
}
```

- [ ] **Step 4: Lancer → succès**

Run: `./mvnw test -Dtest=FakeAntivirusScannerTest` — Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/praxedo/upload/infrastructure/scan/FakeAntivirusScanner.java \
        src/test/java/com/praxedo/upload/infrastructure/scan/FakeAntivirusScannerTest.java
git commit -m "feat(infra): fake antivirus scanner detecting EICAR test signature"
```

---

## Task 10: `FileUploadService` — registerUpload / registerBatch

**Files:**
- Create: `.../application/dto/UploadCommands.java` (records)
- Create: `.../application/FileUploadService.java`
- Test: `.../application/FileUploadServiceTest.java`

- [ ] **Step 1: Test (unitaire, avec adapters in-memory + Clock fixe)**

```java
package com.praxedo.upload.application;

import com.praxedo.upload.application.dto.UploadCommands.*;
import com.praxedo.upload.domain.file.FileStatus;
import com.praxedo.upload.infrastructure.persistence.inmemory.InMemoryFileMetadataRepository;
import com.praxedo.upload.infrastructure.storage.local.LocalFileStorage;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class FileUploadServiceTest {
    private final InMemoryFileMetadataRepository repo = new InMemoryFileMetadataRepository();
    private final LocalFileStorage storage = new LocalFileStorage("/tmp/praxedo-test", "http://localhost:8080");
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-10T10:00:00Z"), ZoneOffset.UTC);
    private int seq = 0;
    private final com.praxedo.upload.domain.port.IdGenerator ids = () -> UUID.nameUUIDFromBytes(("id" + seq++).getBytes());

    private final FileUploadService service = new FileUploadService(repo, storage, ids, clock);
    private final UUID owner = UUID.randomUUID();

    @Test
    void register_upload_creates_pending_and_returns_upload_url() {
        var result = service.registerUpload(owner, new RegisterUploadCommand("a.pdf", "application/pdf", 100L));
        assertThat(result.status()).isEqualTo(FileStatus.PENDING);
        assertThat(result.uploadUrl().toString()).contains("/api/_local/upload");
        assertThat(repo.findByIdAndOwner(result.id(), owner)).isPresent();
    }

    @Test
    void register_batch_shares_one_batch_id() {
        var result = service.registerBatch(owner, new RegisterBatchCommand(List.of(
            new RegisterUploadCommand("a.pdf", "application/pdf", 100L),
            new RegisterUploadCommand("b.pdf", "application/pdf", 200L))));
        assertThat(result.items()).hasSize(2);
        UUID batchId = result.batchId();
        result.items().forEach(i ->
            assertThat(repo.findById(i.id()).orElseThrow().batchId()).isEqualTo(batchId));
    }
}
```

- [ ] **Step 2: Lancer → échec**

Run: `./mvnw test -Dtest=FileUploadServiceTest` — Expected: FAIL.

- [ ] **Step 3: Implémenter DTO + service**

`UploadCommands.java` :
```java
package com.praxedo.upload.application.dto;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.praxedo.upload.domain.file.FileStatus;

public final class UploadCommands {
    private UploadCommands() {}

    public record RegisterUploadCommand(String filename, String contentType, long sizeBytes) {}
    public record RegisterBatchCommand(List<RegisterUploadCommand> files) {}

    public record UploadRegistration(UUID id, FileStatus status, URI uploadUrl, Instant uploadExpiresAt) {}
    public record BatchRegistration(UUID batchId, List<UploadRegistration> items) {}
}
```

`FileUploadService.java` :
```java
package com.praxedo.upload.application;

import com.praxedo.upload.application.dto.UploadCommands.*;
import com.praxedo.upload.domain.file.FileRecord;
import com.praxedo.upload.domain.port.*;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FileUploadService {
    private final FileMetadataRepository repository;
    private final FileStorage storage;
    private final IdGenerator ids;
    private final Clock clock;

    public FileUploadService(FileMetadataRepository repository, FileStorage storage, IdGenerator ids, Clock clock) {
        this.repository = repository;
        this.storage = storage;
        this.ids = ids;
        this.clock = clock;
    }

    public UploadRegistration registerUpload(UUID ownerId, RegisterUploadCommand cmd) {
        return register(ownerId, cmd, null);
    }

    public BatchRegistration registerBatch(UUID ownerId, RegisterBatchCommand cmd) {
        UUID batchId = ids.newId();
        List<UploadRegistration> items = new ArrayList<>();
        for (RegisterUploadCommand file : cmd.files()) {
            items.add(register(ownerId, file, batchId));
        }
        return new BatchRegistration(batchId, items);
    }

    private UploadRegistration register(UUID ownerId, RegisterUploadCommand cmd, UUID batchId) {
        UUID id = ids.newId();
        Instant now = clock.instant();
        String storageKey = ownerId + "/" + id + "/" + cmd.filename();
        FileRecord record = FileRecord.pending(id, ownerId, batchId, cmd.filename(),
            cmd.contentType(), cmd.sizeBytes(), storageKey, now);
        repository.save(record);
        FileStorage.UploadTarget target = storage.createUploadTarget(storageKey, cmd.contentType(), cmd.sizeBytes());
        return new UploadRegistration(id, record.status(), target.url(), target.expiresAt());
    }
}
```

- [ ] **Step 4: Lancer → succès**

Run: `./mvnw test -Dtest=FileUploadServiceTest` — Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/praxedo/upload/application/FileUploadService.java \
        src/main/java/com/praxedo/upload/application/dto/UploadCommands.java \
        src/test/java/com/praxedo/upload/application/FileUploadServiceTest.java
git commit -m "feat(app): FileUploadService (single + batch registration)"
```

---

## Task 11: `FileScanService` — flux de scan (SCANNING → verdict)

**Files:**
- Create: `.../application/FileScanService.java`
- Test: `.../application/FileScanServiceTest.java`
- Create: `.../infrastructure/scan/SynchronousScanQueue.java` (profil `test`)
- Create: `.../infrastructure/scan/InProcessScanQueue.java` (profil `local`, `@Async`)

- [ ] **Step 1: Test**

```java
package com.praxedo.upload.application;

import com.praxedo.upload.domain.file.*;
import com.praxedo.upload.domain.port.AntivirusScanner;
import com.praxedo.upload.infrastructure.persistence.inmemory.InMemoryFileMetadataRepository;
import com.praxedo.upload.infrastructure.scan.FakeAntivirusScanner;
import com.praxedo.upload.infrastructure.storage.local.LocalFileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class FileScanServiceTest {
    private final InMemoryFileMetadataRepository repo = new InMemoryFileMetadataRepository();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-10T10:00:00Z"), ZoneOffset.UTC);
    private final UUID owner = UUID.randomUUID();
    private static final String EICAR =
        "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

    private FileScanService service(LocalFileStorage storage, AntivirusScanner scanner) {
        return new FileScanService(repo, storage, scanner, clock);
    }

    private FileRecord persistPending(LocalFileStorage storage, String name, String body) {
        UUID id = UUID.randomUUID();
        String key = owner + "/" + id + "/" + name;
        storage.write(key, new ByteArrayInputStream(body.getBytes()));
        FileRecord f = FileRecord.pending(id, owner, null, name, "text/plain", body.length(), key, clock.instant());
        repo.save(f);
        return f;
    }

    @Test
    void clean_file_becomes_clean(@TempDir Path dir) {
        LocalFileStorage storage = new LocalFileStorage(dir.toString(), "http://localhost:8080");
        FileRecord f = persistPending(storage, "ok.txt", "contenu sain");
        service(storage, new FakeAntivirusScanner()).scan(f.id());
        assertThat(repo.findById(f.id()).orElseThrow().status()).isEqualTo(FileStatus.CLEAN);
    }

    @Test
    void infected_file_becomes_infected_and_bytes_deleted(@TempDir Path dir) {
        LocalFileStorage storage = new LocalFileStorage(dir.toString(), "http://localhost:8080");
        FileRecord f = persistPending(storage, "virus.txt", EICAR);
        service(storage, new FakeAntivirusScanner()).scan(f.id());
        FileRecord after = repo.findById(f.id()).orElseThrow();
        assertThat(after.status()).isEqualTo(FileStatus.INFECTED);
        assertThat(storage.exists(f.storageKey())).isFalse();
    }

    @Test
    void technical_failure_marks_scan_failed(@TempDir Path dir) {
        LocalFileStorage storage = new LocalFileStorage(dir.toString(), "http://localhost:8080");
        FileRecord f = persistPending(storage, "x.txt", "data");
        AntivirusScanner failing = (in, at) -> { throw new AntivirusScanner.ScanException("moteur KO"); };
        service(storage, failing).scan(f.id());
        assertThat(repo.findById(f.id()).orElseThrow().status()).isEqualTo(FileStatus.SCAN_FAILED);
    }
}
```

- [ ] **Step 2: Lancer → échec**

Run: `./mvnw test -Dtest=FileScanServiceTest` — Expected: FAIL.

- [ ] **Step 3: Implémenter**

`FileScanService.java` :
```java
package com.praxedo.upload.application;

import com.praxedo.upload.domain.file.*;
import com.praxedo.upload.domain.port.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class FileScanService {
    private static final Logger log = LoggerFactory.getLogger(FileScanService.class);

    private final FileMetadataRepository repository;
    private final FileStorage storage;
    private final AntivirusScanner scanner;
    private final Clock clock;

    public FileScanService(FileMetadataRepository repository, FileStorage storage,
                           AntivirusScanner scanner, Clock clock) {
        this.repository = repository;
        this.storage = storage;
        this.scanner = scanner;
        this.clock = clock;
    }

    public void scan(UUID fileId) {
        FileRecord file = repository.findById(fileId).orElse(null);
        if (file == null) {
            log.warn("scan demandé pour un fichier inconnu: {}", fileId);
            return;
        }
        Instant now = clock.instant();
        file.markScanning(now);
        repository.save(file);
        try (InputStream content = storage.read(file.storageKey())) {
            ScanVerdict verdict = scanner.scan(content, clock.instant());
            if (verdict.infected()) {
                file.markInfected(verdict, clock.instant());
                repository.save(file);
                storage.delete(file.storageKey()); // les octets infectés ne restent pas servables
                log.info("fichier {} INFECTED ({})", fileId, verdict.threatName());
            } else {
                file.markClean(verdict, clock.instant());
                repository.save(file);
                log.info("fichier {} CLEAN", fileId);
            }
        } catch (AntivirusScanner.ScanException | java.io.IOException e) {
            file.markScanFailed(clock.instant());
            repository.save(file);
            log.error("échec technique du scan pour {} : {}", fileId, e.getMessage());
        }
    }
}
```

Puis les deux adapters de queue (même port `ScanQueue`, sélectionnés par profil).

`SynchronousScanQueue.java` (profil `test` — flux déterministe, scan sur le thread appelant) :
```java
package com.praxedo.upload.infrastructure.scan;

import com.praxedo.upload.application.FileScanService;
import com.praxedo.upload.domain.port.ScanQueue;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class SynchronousScanQueue implements ScanQueue {
    private final FileScanService scanService;
    public SynchronousScanQueue(FileScanService scanService) { this.scanService = scanService; }
    @Override public void enqueue(ScanRequest request) { scanService.scan(request.fileId()); }
}
```

`InProcessScanQueue.java` (profil `local` — asynchrone dans le même processus ; remplacé par Pub/Sub au jalon 2) :
```java
package com.praxedo.upload.infrastructure.scan;

import com.praxedo.upload.application.FileScanService;
import com.praxedo.upload.domain.port.ScanQueue;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class InProcessScanQueue implements ScanQueue {
    private final FileScanService scanService;
    public InProcessScanQueue(FileScanService scanService) { this.scanService = scanService; }

    @Async
    @Override public void enqueue(ScanRequest request) { scanService.scan(request.fileId()); }
}
```

> `@Async` s'appuie sur `@EnableAsync` (Task 13). Le profil `test` utilise la version **synchrone** → les tests web (`/rescan` puis lecture du statut) sont déterministes, sans course.

- [ ] **Step 4: Lancer → succès**

Run: `./mvnw test -Dtest=FileScanServiceTest` — Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/praxedo/upload/application/FileScanService.java \
        src/main/java/com/praxedo/upload/infrastructure/scan/SynchronousScanQueue.java \
        src/main/java/com/praxedo/upload/infrastructure/scan/InProcessScanQueue.java \
        src/test/java/com/praxedo/upload/application/FileScanServiceTest.java
git commit -m "feat(app): FileScanService + profile-based scan queues (sync/test, async/local)"
```

---

## Task 12: `FileQueryService` + `FileDownloadService` (gate CLEAN) + `ReconciliationService`

**Files:**
- Create: `.../application/dto/FileViews.java`
- Create: `.../application/FileQueryService.java`, `FileDownloadService.java`, `ReconciliationService.java`
- Test: `.../application/FileDownloadServiceTest.java`, `.../application/ReconciliationServiceTest.java`

- [ ] **Step 1: Tests (download gate + réconciliation)**

`FileDownloadServiceTest.java` :
```java
package com.praxedo.upload.application;

import com.praxedo.upload.domain.file.*;
import com.praxedo.upload.domain.file.exceptions.*;
import com.praxedo.upload.infrastructure.persistence.inmemory.InMemoryFileMetadataRepository;
import com.praxedo.upload.infrastructure.storage.local.LocalFileStorage;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class FileDownloadServiceTest {
    private final InMemoryFileMetadataRepository repo = new InMemoryFileMetadataRepository();
    private final LocalFileStorage storage = new LocalFileStorage("/tmp/dl-test", "http://localhost:8080");
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-10T10:00:00Z"), ZoneOffset.UTC);
    private final StorageProperties props = new StorageProperties(
        new StorageProperties.Local("/tmp/dl-test"), Duration.ofMinutes(15), Duration.ofMinutes(5), "http://localhost:8080");
    private final FileDownloadService service = new FileDownloadService(repo, storage, props);
    private final UUID owner = UUID.randomUUID();

    private FileRecord persisted(FileStatus status) {
        UUID id = UUID.randomUUID();
        FileRecord f = FileRecord.pending(id, owner, null, "a.txt", "text/plain", 3, owner + "/" + id + "/a.txt", clock.instant());
        if (status != FileStatus.PENDING) { f.markScanning(clock.instant()); }
        if (status == FileStatus.CLEAN) f.markClean(ScanVerdict.clean("fake", clock.instant()), clock.instant());
        if (status == FileStatus.INFECTED) f.markInfected(ScanVerdict.infected("fake", "Eicar", clock.instant()), clock.instant());
        repo.save(f);
        return f;
    }

    @Test
    void clean_file_yields_a_download_url() {
        FileRecord f = persisted(FileStatus.CLEAN);
        assertThat(service.requestDownload(owner, f.id()).toString()).contains("/api/_local/download");
    }

    @Test
    void non_clean_file_is_refused() {
        FileRecord f = persisted(FileStatus.INFECTED);
        assertThatThrownBy(() -> service.requestDownload(owner, f.id()))
            .isInstanceOf(DownloadNotAllowedException.class);
    }

    @Test
    void other_owner_cannot_download() {
        FileRecord f = persisted(FileStatus.CLEAN);
        assertThatThrownBy(() -> service.requestDownload(UUID.randomUUID(), f.id()))
            .isInstanceOf(FileNotFoundException.class);
    }
}
```

`ReconciliationServiceTest.java` :
```java
package com.praxedo.upload.application;

import com.praxedo.upload.domain.file.*;
import com.praxedo.upload.infrastructure.persistence.inmemory.InMemoryFileMetadataRepository;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationServiceTest {
    private final InMemoryFileMetadataRepository repo = new InMemoryFileMetadataRepository();
    private final Instant t0 = Instant.parse("2026-07-10T10:00:00Z");
    private final Clock later = Clock.fixed(t0.plus(Duration.ofHours(2)), ZoneOffset.UTC);
    private final UUID owner = UUID.randomUUID();

    @Test
    void stale_pending_becomes_expired() {
        FileRecord f = FileRecord.pending(UUID.randomUUID(), owner, null, "a.txt", "text/plain", 1, "k", t0);
        repo.save(f);
        ReconciliationService service = new ReconciliationService(repo, later, Duration.ofHours(1));
        service.expireStalePending();
        assertThat(repo.findById(f.id()).orElseThrow().status()).isEqualTo(FileStatus.EXPIRED);
    }
}
```

- [ ] **Step 2: Lancer → échec**

Run: `./mvnw test -Dtest=FileDownloadServiceTest,ReconciliationServiceTest` — Expected: FAIL.

- [ ] **Step 3: Implémenter DTO + services**

`FileViews.java` :
```java
package com.praxedo.upload.application.dto;

import com.praxedo.upload.domain.file.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class FileViews {
    private FileViews() {}

    public record FileView(UUID id, String filename, String contentType, long sizeBytes,
                           FileStatus status, boolean infected, String threatName,
                           Instant createdAt, Instant scannedAt) {
        public static FileView of(FileRecord f) {
            ScanVerdict v = f.scanVerdict();
            return new FileView(f.id(), f.filename(), f.contentType(), f.sizeBytes(), f.status(),
                v != null && v.infected(), v == null ? null : v.threatName(), f.createdAt(), f.scannedAt());
        }
    }

    public record BatchView(UUID batchId, List<FileView> items, StatusCounts summary) {}
}
```

`FileQueryService.java` :
```java
package com.praxedo.upload.application;

import com.praxedo.upload.application.dto.FileViews.*;
import com.praxedo.upload.domain.file.*;
import com.praxedo.upload.domain.file.exceptions.FileNotFoundException;
import com.praxedo.upload.domain.port.FileMetadataRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
public class FileQueryService {
    private final FileMetadataRepository repository;

    public FileQueryService(FileMetadataRepository repository) { this.repository = repository; }

    public PageResult<FileView> list(FileQuery query) {
        PageResult<FileRecord> page = repository.search(query);
        List<FileView> views = page.items().stream().map(FileView::of).toList();
        return new PageResult<>(views, page.page(), page.size(), page.totalElements());
    }

    public StatusCounts stats(UUID ownerId) { return repository.countByOwner(ownerId); }

    public FileView getFile(UUID ownerId, UUID fileId) {
        return repository.findByIdAndOwner(fileId, ownerId).map(FileView::of)
            .orElseThrow(() -> new FileNotFoundException(fileId));
    }

    public BatchView getBatch(UUID ownerId, UUID batchId) {
        List<FileRecord> files = repository.findByBatchAndOwner(batchId, ownerId);
        if (files.isEmpty()) throw new FileNotFoundException(batchId);
        List<FileView> items = files.stream().map(FileView::of).toList();
        long clean = files.stream().filter(f -> f.status() == FileStatus.CLEAN).count();
        long scanning = files.stream().filter(f -> f.status() == FileStatus.SCANNING).count();
        long pending = files.stream().filter(f -> f.status() == FileStatus.PENDING).count();
        long blocked = files.stream().filter(f -> f.status() == FileStatus.INFECTED).count();
        return new BatchView(batchId, items, new StatusCounts(files.size(), clean, scanning, pending, blocked));
    }
}
```

`FileDownloadService.java` :
```java
package com.praxedo.upload.application;

import com.praxedo.upload.domain.file.FileRecord;
import com.praxedo.upload.domain.file.exceptions.*;
import com.praxedo.upload.domain.port.FileMetadataRepository;
import com.praxedo.upload.domain.port.FileStorage;
import com.praxedo.upload.infrastructure.config.StorageProperties;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.util.UUID;

@Service
public class FileDownloadService {
    private final FileMetadataRepository repository;
    private final FileStorage storage;
    private final StorageProperties properties;

    public FileDownloadService(FileMetadataRepository repository, FileStorage storage, StorageProperties properties) {
        this.repository = repository;
        this.storage = storage;
        this.properties = properties;
    }

    /** Applique l'invariant : URL émise uniquement si CLEAN. */
    public URI requestDownload(UUID ownerId, UUID fileId) {
        FileRecord file = repository.findByIdAndOwner(fileId, ownerId)
            .orElseThrow(() -> new FileNotFoundException(fileId));
        if (!file.isDownloadable()) {
            throw new DownloadNotAllowedException(file.status());
        }
        return storage.createDownloadUrl(file.storageKey(), properties.downloadUrlTtl());
    }
}
```

`ReconciliationService.java` :
```java
package com.praxedo.upload.application;

import com.praxedo.upload.domain.file.*;
import com.praxedo.upload.domain.port.FileMetadataRepository;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class ReconciliationService {
    private final FileMetadataRepository repository;
    private final Clock clock;
    private final Duration pendingTtl;

    public ReconciliationService(FileMetadataRepository repository, Clock clock,
            @org.springframework.beans.factory.annotation.Value("${scan.pending-ttl:PT1H}") Duration pendingTtl) {
        this.repository = repository;
        this.clock = clock;
        this.pendingTtl = pendingTtl;
    }

    public void expireStalePending() {
        Instant threshold = clock.instant().minus(pendingTtl);
        for (FileRecord f : repository.findByStatusOlderThan(FileStatus.PENDING, threshold)) {
            f.markExpired(clock.instant());
            repository.save(f);
        }
    }
}
```

- [ ] **Step 4: Lancer → succès**

Run: `./mvnw test -Dtest=FileDownloadServiceTest,ReconciliationServiceTest` — Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/praxedo/upload/application/ src/test/java/com/praxedo/upload/application/
git commit -m "feat(app): query, download gate and reconciliation services"
```

---

## Task 13: `ApiKeyService` + config (Clock, @EnableAsync, seed clients)

**Files:**
- Create: `.../application/ApiKeyService.java`
- Create: `.../infrastructure/config/AppConfig.java`, `LocalSeedConfig.java`
- Test: `.../application/ApiKeyServiceTest.java`

- [ ] **Step 1: Test**

```java
package com.praxedo.upload.application;

import com.praxedo.upload.domain.client.ApiClient;
import com.praxedo.upload.infrastructure.persistence.inmemory.InMemoryApiClientRepository;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyServiceTest {
    private final InMemoryApiClientRepository repo = new InMemoryApiClientRepository();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-10T10:00:00Z"), ZoneOffset.UTC);
    private final ApiKeyService service = new ApiKeyService(repo, () -> UUID.randomUUID(), clock);

    @Test
    void created_client_can_be_resolved_by_its_raw_key() {
        String rawKey = service.createClient("Bâtir SA");
        Optional<ApiClient> resolved = service.resolveOwner(rawKey);
        assertThat(resolved).isPresent();
        assertThat(resolved.get().name()).isEqualTo("Bâtir SA");
    }

    @Test
    void unknown_key_resolves_to_empty() {
        assertThat(service.resolveOwner("nope")).isEmpty();
    }

    @Test
    void raw_key_is_never_stored_in_clear() {
        String rawKey = service.createClient("X");
        assertThat(repo.findByApiKeyHash(rawKey)).isEmpty(); // stocké haché, pas en clair
    }
}
```

- [ ] **Step 2: Lancer → échec**

Run: `./mvnw test -Dtest=ApiKeyServiceTest` — Expected: FAIL.

- [ ] **Step 3: Implémenter**

`ApiKeyService.java` :
```java
package com.praxedo.upload.application;

import com.praxedo.upload.domain.client.ApiClient;
import com.praxedo.upload.domain.port.ApiClientRepository;
import com.praxedo.upload.domain.port.IdGenerator;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.Optional;

@Service
public class ApiKeyService {
    private final ApiClientRepository repository;
    private final IdGenerator ids;
    private final Clock clock;

    public ApiKeyService(ApiClientRepository repository, IdGenerator ids, Clock clock) {
        this.repository = repository;
        this.ids = ids;
        this.clock = clock;
    }

    /** Génère une clé, stocke son hash, renvoie la clé en clair UNE seule fois. */
    public String createClient(String name) {
        String rawKey = "pk_" + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(java.util.UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        ApiClient client = new ApiClient(ids.newId(), name, hash(rawKey), true, clock.instant());
        repository.save(client);
        return rawKey;
    }

    public Optional<ApiClient> resolveOwner(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) return Optional.empty();
        return repository.findByApiKeyHash(hash(rawKey));
    }

    private String hash(String rawKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
```

`AppConfig.java` (fournit `Clock`, active l'async) :
```java
package com.praxedo.upload.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.Clock;

@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(StorageProperties.class)
public class AppConfig {
    @Bean
    public Clock clock() { return Clock.systemUTC(); }
}
```

`LocalSeedConfig.java` (crée un client de démo au démarrage en profil local et logue sa clé) :
```java
package com.praxedo.upload.infrastructure.config;

import com.praxedo.upload.application.ApiKeyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("local")
public class LocalSeedConfig {
    private static final Logger log = LoggerFactory.getLogger(LocalSeedConfig.class);

    @Bean
    public CommandLineRunner seedApiClient(ApiKeyService apiKeyService) {
        return args -> {
            String key = apiKeyService.createClient("Bâtir SA (démo locale)");
            log.info("=== CLÉ API DE DÉMO (profil local) : {} ===", key);
        };
    }
}
```

- [ ] **Step 4: Lancer → succès**

Run: `./mvnw test -Dtest=ApiKeyServiceTest` — Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/praxedo/upload/application/ApiKeyService.java \
        src/main/java/com/praxedo/upload/infrastructure/config/
git commit -m "feat(app): ApiKeyService (hashed keys) + app config and local seed"
```

---

## Task 14: Sécurité web — `ApiKeyAuthFilter` + `SecurityConfig`

**Files:**
- Create: `.../infrastructure/web/ApiKeyAuthFilter.java`
- Create: `.../infrastructure/web/AuthenticatedClient.java`
- Create: `.../infrastructure/config/SecurityConfig.java`
- Test: `.../infrastructure/web/ApiKeyAuthFilterTest.java`

- [ ] **Step 1: Test (MockMvc minimal via un contrôleur ping)**

```java
package com.praxedo.upload.infrastructure.web;

import com.praxedo.upload.application.ApiKeyService;
import com.praxedo.upload.domain.client.ApiClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyAuthFilterTest {
    private final ApiKeyService apiKeyService = Mockito.mock(ApiKeyService.class);
    private final ApiKeyAuthFilter filter = new ApiKeyAuthFilter(apiKeyService);

    @Test
    void valid_key_sets_authentication() throws Exception {
        UUID ownerId = UUID.randomUUID();
        Mockito.when(apiKeyService.resolveOwner("good"))
            .thenReturn(Optional.of(new ApiClient(ownerId, "X", "h", true, Instant.now())));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-API-Key", "good");
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, new MockHttpServletResponse(), chain);
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(((AuthenticatedClient) auth.getPrincipal()).ownerId()).isEqualTo(ownerId);
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void missing_key_leaves_context_empty() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);
        assertThat(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
```

- [ ] **Step 2: Lancer → échec**

Run: `./mvnw test -Dtest=ApiKeyAuthFilterTest` — Expected: FAIL.

- [ ] **Step 3: Implémenter**

`AuthenticatedClient.java` :
```java
package com.praxedo.upload.infrastructure.web;

import java.util.UUID;

public record AuthenticatedClient(UUID ownerId, String name) {}
```

`ApiKeyAuthFilter.java` :
```java
package com.praxedo.upload.infrastructure.web;

import com.praxedo.upload.application.ApiKeyService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

public class ApiKeyAuthFilter extends OncePerRequestFilter {
    private static final String HEADER = "X-API-Key";
    private final ApiKeyService apiKeyService;

    public ApiKeyAuthFilter(ApiKeyService apiKeyService) { this.apiKeyService = apiKeyService; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String rawKey = request.getHeader(HEADER);
        apiKeyService.resolveOwner(rawKey).ifPresent(client -> {
            AuthenticatedClient principal = new AuthenticatedClient(client.id(), client.name());
            var auth = new UsernamePasswordAuthenticationToken(
                principal, null, AuthorityUtils.createAuthorityList("ROLE_CLIENT"));
            SecurityContextHolder.getContext().setAuthentication(auth);
        });
        chain.doFilter(request, response);
    }
}
```

`SecurityConfig.java` :
```java
package com.praxedo.upload.infrastructure.config;

import com.praxedo.upload.application.ApiKeyService;
import com.praxedo.upload.infrastructure.web.ApiKeyAuthFilter;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ApiKeyService apiKeyService) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/_local/**", "/actuator/health").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            // renvoyer 401 (et non 403) quand aucune clé valide n'est présente
            .exceptionHandling(e -> e.authenticationEntryPoint(
                (req, res, ex) -> res.sendError(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED)))
            .addFilterBefore(new ApiKeyAuthFilter(apiKeyService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

> Le proxy local (`/api/_local/**`) est ouvert car il représente GCS (qui vérifie ses propres URLs signées) ; en prod ce chemin n'existe pas.

- [ ] **Step 4: Lancer → succès**

Run: `./mvnw test -Dtest=ApiKeyAuthFilterTest` — Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/praxedo/upload/infrastructure/web/ApiKeyAuthFilter.java \
        src/main/java/com/praxedo/upload/infrastructure/web/AuthenticatedClient.java \
        src/main/java/com/praxedo/upload/infrastructure/config/SecurityConfig.java \
        src/test/java/com/praxedo/upload/infrastructure/web/ApiKeyAuthFilterTest.java
git commit -m "feat(web): API key authentication filter and security config"
```

---

## Task 15: Contrôleurs REST + `GlobalExceptionHandler` + proxy local

**Files:**
- Create: `.../infrastructure/web/dto/ApiDtos.java`
- Create: `.../infrastructure/web/FilesController.java`, `BatchesController.java`, `LocalStorageController.java`, `GlobalExceptionHandler.java`
- Test: `.../infrastructure/web/FilesControllerTest.java`

- [ ] **Step 1: Test web (MockMvc, @SpringBootTest, profil test)**

```java
package com.praxedo.upload.infrastructure.web;

import com.praxedo.upload.application.ApiKeyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FilesControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ApiKeyService apiKeyService;
    @Autowired ObjectMapper json;
    String key;

    @BeforeEach void setup() { key = apiKeyService.createClient("Test SA"); }

    @Test
    void unauthenticated_request_is_401() throws Exception {
        mvc.perform(get("/api/files")).andExpect(status().isUnauthorized());
    }

    @Test
    void register_upload_returns_201_with_upload_url() throws Exception {
        String body = json.writeValueAsString(new java.util.HashMap<>() {{
            put("filename", "a.pdf"); put("contentType", "application/pdf"); put("size", 100);
        }});
        mvc.perform(post("/api/files").header("X-API-Key", key)
                .contentType("application/json").content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.uploadUrl").exists());
    }

    @Test
    void full_flow_clean_file_is_downloadable() throws Exception {
        // 1. enregistrer
        String body = "{\"filename\":\"ok.txt\",\"contentType\":\"text/plain\",\"size\":5}";
        String res = mvc.perform(post("/api/files").header("X-API-Key", key)
                .contentType("application/json").content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var node = json.readTree(res);
        String id = node.get("id").asText();
        String uploadUrl = node.get("uploadUrl").asText();
        String uploadPath = uploadUrl.substring(uploadUrl.indexOf("/api/_local/upload"));
        // 2. uploader les octets via le proxy local
        mvc.perform(put(uploadPath).content("hello")).andExpect(status().isOk());
        // 3. déclencher le scan (le proxy publie sur la queue ; en test on force le scan)
        mvc.perform(post("/api/files/" + id + "/rescan").header("X-API-Key", key))
            .andExpect(status().isAccepted());
        // 4. statut CLEAN
        mvc.perform(get("/api/files/" + id).header("X-API-Key", key))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLEAN"));
        // 5. download autorisé
        mvc.perform(get("/api/files/" + id + "/content").header("X-API-Key", key))
            .andExpect(status().is3xxRedirection());
    }
}
```

> Déterminisme : `POST /api/files/{id}/rescan` appelle `scanQueue.enqueue(...)`. En profil `test`, le bean `ScanQueue` est `SynchronousScanQueue` → le scan s'exécute sur le thread appelant, donc le statut est déjà à jour à l'étape suivante (aucune course).

- [ ] **Step 2: Lancer → échec**

Run: `./mvnw test -Dtest=FilesControllerTest` — Expected: FAIL.

- [ ] **Step 3: Implémenter DTO + contrôleurs + handler**

`ApiDtos.java` :
```java
package com.praxedo.upload.infrastructure.web.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public final class ApiDtos {
    private ApiDtos() {}

    public record RegisterFileRequest(
        @NotBlank String filename,
        @NotBlank String contentType,
        @Positive long size) {}

    public record RegisterBatchRequest(@NotEmpty List<@Valid RegisterFileRequest> files) {}
}
```

`GlobalExceptionHandler.java` :
```java
package com.praxedo.upload.infrastructure.web;

import com.praxedo.upload.domain.file.exceptions.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(FileNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(FileNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }
    @ExceptionHandler(DownloadNotAllowedException.class)
    public ResponseEntity<Map<String, String>> forbidden(DownloadNotAllowedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
    }
    @ExceptionHandler(IllegalFileTransitionException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalFileTransitionException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }
}
```

`FilesController.java` :
```java
package com.praxedo.upload.infrastructure.web;

import com.praxedo.upload.application.*;
import com.praxedo.upload.application.dto.UploadCommands.*;
import com.praxedo.upload.domain.file.*;
import com.praxedo.upload.domain.port.ScanQueue;
import com.praxedo.upload.infrastructure.web.dto.ApiDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public class FilesController {
    private final FileUploadService uploadService;
    private final FileQueryService queryService;
    private final FileDownloadService downloadService;
    private final ScanQueue scanQueue;

    public FilesController(FileUploadService uploadService, FileQueryService queryService,
                           FileDownloadService downloadService, ScanQueue scanQueue) {
        this.uploadService = uploadService;
        this.queryService = queryService;
        this.downloadService = downloadService;
        this.scanQueue = scanQueue;
    }

    @PostMapping
    public ResponseEntity<UploadRegistration> register(@AuthenticationPrincipal AuthenticatedClient client,
                                                        @Valid @RequestBody RegisterFileRequest req) {
        UploadRegistration reg = uploadService.registerUpload(client.ownerId(),
            new RegisterUploadCommand(req.filename(), req.contentType(), req.size()));
        return ResponseEntity.status(HttpStatus.CREATED).body(reg);
    }

    @GetMapping
    public PageResult<?> list(@AuthenticationPrincipal AuthenticatedClient client,
                              @RequestParam(required = false) String q,
                              @RequestParam(required = false) FileStatus status,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "20") int size) {
        return queryService.list(FileQuery.of(client.ownerId(), q, status, page, size));
    }

    @GetMapping("/stats")
    public StatusCounts stats(@AuthenticationPrincipal AuthenticatedClient client) {
        return queryService.stats(client.ownerId());
    }

    @GetMapping("/{id}")
    public Object get(@AuthenticationPrincipal AuthenticatedClient client, @PathVariable UUID id) {
        return queryService.getFile(client.ownerId(), id);
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<Void> download(@AuthenticationPrincipal AuthenticatedClient client, @PathVariable UUID id) {
        URI url = downloadService.requestDownload(client.ownerId(), id);
        return ResponseEntity.status(HttpStatus.FOUND).location(url).build();
    }

    @PostMapping("/{id}/rescan")
    public ResponseEntity<Void> rescan(@AuthenticationPrincipal AuthenticatedClient client, @PathVariable UUID id) {
        // vérifie la propriété avant de (re)mettre en file
        queryService.getFile(client.ownerId(), id);
        scanQueue.enqueue(new ScanQueue.ScanRequest(id));
        return ResponseEntity.accepted().build();
    }
}
```

`BatchesController.java` :
```java
package com.praxedo.upload.infrastructure.web;

import com.praxedo.upload.application.*;
import com.praxedo.upload.application.dto.FileViews.BatchView;
import com.praxedo.upload.application.dto.UploadCommands.*;
import com.praxedo.upload.infrastructure.web.dto.ApiDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/batches")
public class BatchesController {
    private final FileUploadService uploadService;
    private final FileQueryService queryService;

    public BatchesController(FileUploadService uploadService, FileQueryService queryService) {
        this.uploadService = uploadService;
        this.queryService = queryService;
    }

    @PostMapping
    public ResponseEntity<BatchRegistration> register(@AuthenticationPrincipal AuthenticatedClient client,
                                                       @Valid @RequestBody RegisterBatchRequest req) {
        var commands = req.files().stream()
            .map(f -> new RegisterUploadCommand(f.filename(), f.contentType(), f.size())).toList();
        BatchRegistration reg = uploadService.registerBatch(client.ownerId(), new RegisterBatchCommand(commands));
        return ResponseEntity.status(HttpStatus.CREATED).body(reg);
    }

    @GetMapping("/{batchId}")
    public BatchView get(@AuthenticationPrincipal AuthenticatedClient client, @PathVariable UUID batchId) {
        return queryService.getBatch(client.ownerId(), batchId);
    }
}
```

`LocalStorageController.java` (proxy GCS en local) :
```java
package com.praxedo.upload.infrastructure.web;

import com.praxedo.upload.infrastructure.storage.local.LocalFileStorage;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/_local")
@Profile({"local", "test"})
public class LocalStorageController {
    private final LocalFileStorage storage;

    public LocalStorageController(LocalFileStorage storage) { this.storage = storage; }

    @PutMapping("/upload")
    public ResponseEntity<Void> upload(@RequestParam String key, HttpServletRequest request) throws Exception {
        storage.write(key, request.getInputStream());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> download(@RequestParam String key) {
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(new InputStreamResource(storage.read(key)));
    }
}
```

- [ ] **Step 4: Lancer → succès**

Run: `./mvnw test -Dtest=FilesControllerTest` — Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/praxedo/upload/infrastructure/web/ src/test/java/com/praxedo/upload/infrastructure/web/FilesControllerTest.java
git commit -m "feat(web): REST controllers, batch, local proxy and exception handling"
```

---

## Task 16: Test d'intégration bout-en-bout (chemin sain + infecté) + README backend

**Files:**
- Test: `.../EndToEndFlowTest.java`
- Create: `praxedo-upload-backend/README.md`
- Create: `praxedo-upload-backend/src/test/resources/application-test.yml` (base-dir temporaire)

- [ ] **Step 1: Test bout-en-bout**

```java
package com.praxedo.upload;

import com.praxedo.upload.application.ApiKeyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EndToEndFlowTest {
    @Autowired MockMvc mvc;
    @Autowired ApiKeyService apiKeyService;
    @Autowired ObjectMapper json;

    private static final String EICAR =
        "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

    private String register(String key, String name) throws Exception {
        String body = "{\"filename\":\"" + name + "\",\"contentType\":\"text/plain\",\"size\":5}";
        String res = mvc.perform(post("/api/files").header("X-API-Key", key)
                .contentType("application/json").content(body))
            .andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("id").asText();
    }

    private String uploadPath(String key, String id) throws Exception {
        String res = mvc.perform(get("/api/files/" + id).header("X-API-Key", key))
            .andReturn().getResponse().getContentAsString();
        return "/api/_local/upload?key=" + java.net.URLEncoder.encode(
            json.readTree(res).get("id").asText(), java.nio.charset.StandardCharsets.UTF_8); // placeholder si besoin
    }

    @Test
    void infected_file_is_blocked_from_download() throws Exception {
        String key = apiKeyService.createClient("Owner A");
        // register + récupérer l'uploadUrl directement de la réponse de création
        String body = "{\"filename\":\"virus.txt\",\"contentType\":\"text/plain\",\"size\":68}";
        var created = json.readTree(mvc.perform(post("/api/files").header("X-API-Key", key)
                .contentType("application/json").content(body))
            .andReturn().getResponse().getContentAsString());
        String id = created.get("id").asText();
        String up = created.get("uploadUrl").asText();
        String upPath = up.substring(up.indexOf("/api/_local/upload"));
        mvc.perform(put(upPath).content(EICAR)).andExpect(status().isOk());
        mvc.perform(post("/api/files/" + id + "/rescan").header("X-API-Key", key)).andExpect(status().isAccepted());
        mvc.perform(get("/api/files/" + id).header("X-API-Key", key))
            .andExpect(jsonPath("$.status").value("INFECTED"));
        mvc.perform(get("/api/files/" + id + "/content").header("X-API-Key", key))
            .andExpect(status().isForbidden());
    }

    @Test
    void owner_isolation_is_enforced() throws Exception {
        String keyA = apiKeyService.createClient("Owner A");
        String keyB = apiKeyService.createClient("Owner B");
        String id = register(keyA, "a.txt");
        mvc.perform(get("/api/files/" + id).header("X-API-Key", keyB))
            .andExpect(status().isNotFound());
    }
}
```

> Le helper `uploadPath` n'est pas utilisé par les tests retenus (l'URL vient de la réponse de création) ; le retirer si l'IDE signale du code mort.

- [ ] **Step 2: `application-test.yml`**

```yaml
storage:
  local:
    base-dir: ${java.io.tmpdir}/praxedo-e2e
  upload-url-ttl: PT15M
  download-url-ttl: PT5M
  public-base-url: http://localhost
scan:
  pending-ttl: PT1H
```

- [ ] **Step 3: Lancer toute la suite**

Run: `./mvnw test`
Expected: tous les tests PASS (unitaires domaine/application + web + e2e).

- [ ] **Step 4: README backend**

Écrire `praxedo-upload-backend/README.md` couvrant : objectif, architecture hexagonale (domaine/ports/adapters), invariant de sécurité, machine à états, comment lancer en local (`./mvnw spring-boot:run -Dspring-boot.run.profiles=local`, récupérer la clé de démo dans les logs), exemples `curl` (register → upload proxy → rescan → download), et la note « choix techniques / hypothèses / pistes d'amélioration » renvoyant à la spec.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/praxedo/upload/EndToEndFlowTest.java \
        src/test/resources/application-test.yml README.md
git commit -m "test: end-to-end flow (infected blocked, owner isolation) + backend README"
```

---

## Self-review (à faire après implémentation)

- **Couverture spec (jalon 1)** : upload unitaire + batch (D13) ✓, machine à états (D5) ✓, invariant download (D2/§2) ✓, ports & DI (D7/D12) ✓, clés API par-client + scoping owner (D9) ✓, réconciliation (§5) ✓. **Hors jalon 1** (jalons suivants) : ClamAV réel, Pub/Sub, GCS/Cloud SQL, signed URLs réelles, notifications GCS.
- **Placeholders** : le helper `uploadPath` (Task 16) est explicitement marqué à supprimer ; aucun autre TODO.
- **Cohérence des types** : `AuthenticatedClient.ownerId()`, `FileStorage.UploadTarget`, `ScanQueue.ScanRequest`, `PageResult<T>` utilisés de façon homogène entre tâches.

---

## Roadmap des plans suivants (hors de ce fichier)

- **Jalon 2 — Antivirus réel & async** : adapter `ClamavScanner` (protocole clamd INSTREAM), adapter `PubSubScanQueue` + endpoint push `/internal/scan-events`, profil `worker`. Docker Compose local avec ClamAV. Tests via Testcontainers ClamAV.
- **Jalon 3 — Adapters GCP** : `GcsFileStorage` (URLs signées V4 via IAM signBlob), `JpaFileMetadataRepository` + `JpaApiClientRepository` (Cloud SQL Postgres, Flyway), notification GCS `object finalize` → Pub/Sub. Tests via Testcontainers Postgres.
- **Plan Frontend** (`praxedo-upload-ui`) : React + Vite + react-query, maquette Cloud Design (palette Praxedo).
- **Plan Infra** : `Makefile` + commandes `gcloud`, GitHub Actions, Secret Manager, déploiement Cloud Run (api/worker/ui).
