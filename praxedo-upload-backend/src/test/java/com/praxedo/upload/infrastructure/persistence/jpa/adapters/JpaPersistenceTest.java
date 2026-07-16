package com.praxedo.upload.infrastructure.persistence.jpa.adapters;

import com.praxedo.upload.domain.models.ApiClient;
import com.praxedo.upload.domain.models.FileQuery;
import com.praxedo.upload.domain.models.FileRecord;
import com.praxedo.upload.domain.models.FileStatus;
import com.praxedo.upload.domain.models.PageResult;
import com.praxedo.upload.domain.models.ScanVerdict;
import com.praxedo.upload.domain.models.StatusCounts;
import com.praxedo.upload.infrastructure.persistence.jpa.repositories.ApiClientJpaRepository;
import com.praxedo.upload.infrastructure.persistence.jpa.repositories.FileJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests d'integration de la persistance JPA contre un vrai PostgreSQL (Testcontainers). */
@DataJpaTest(properties = {
    "spring.autoconfigure.exclude=",           // annule l'exclusion de l'application.yml
    "spring.jpa.hibernate.ddl-auto=none",       // Flyway est la source de verite du schema
    "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class JpaPersistenceTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Reproduit la condition de prod (pooler Supabase transaction) : prepared statements
        // serveur desactives. Sans cela, pgjdbc infere le type des parametres cote serveur et
        // masque le bug lower(bytea) sur la recherche a parametre null (voir application-gcp.yml).
        registry.add("spring.datasource.hikari.data-source-properties.prepareThreshold", () -> "0");
    }

    @Autowired
    FileJpaRepository fileJpa;
    @Autowired
    ApiClientJpaRepository apiClientJpa;

    JpaFileMetadataRepository fileRepo;
    JpaApiClientRepository apiClientRepo;

    private final UUID owner = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-07-12T10:00:00Z");

    @BeforeEach
    void setup() {
        fileRepo = new JpaFileMetadataRepository(fileJpa);
        apiClientRepo = new JpaApiClientRepository(apiClientJpa);
    }

    private FileRecord pending(String name, UUID batchId) {
        UUID id = UUID.randomUUID();
        return FileRecord.pending(id, owner, batchId, name, "text/plain", 42, owner + "/" + id + "/" + name, now);
    }

    @Test
    void save_and_reload_preserves_state_and_verdict() {
        FileRecord f = pending("rapport.pdf", null);
        f.markScanning(now);
        f.markInfected(ScanVerdict.infected("clamav", "Eicar-Test", now), now);
        fileRepo.save(f);

        FileRecord loaded = fileRepo.findById(f.id()).orElseThrow();
        assertThat(loaded.status()).isEqualTo(FileStatus.INFECTED);
        assertThat(loaded.scanVerdict().infected()).isTrue();
        assertThat(loaded.scanVerdict().threatName()).isEqualTo("Eicar-Test");
        assertThat(loaded.storageKey()).isEqualTo(f.storageKey());
        assertThat(loaded.scanAttempts()).isEqualTo(1);
    }

    @Test
    void owner_scoping_and_storage_key_lookup() {
        FileRecord f = pending("a.txt", null);
        fileRepo.save(f);
        assertThat(fileRepo.findByIdAndOwner(f.id(), owner)).isPresent();
        assertThat(fileRepo.findByIdAndOwner(f.id(), UUID.randomUUID())).isEmpty();
        assertThat(fileRepo.findByStorageKey(f.storageKey())).isPresent();
    }

    @Test
    void search_filters_by_owner_status_and_query() {
        fileRepo.save(pending("rapport-intervention.pdf", null));
        fileRepo.save(pending("photo.zip", null));
        PageResult<FileRecord> page = fileRepo.search(FileQuery.of(owner, "rapport", null, 0, 20));
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).filename()).isEqualTo("rapport-intervention.pdf");
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    void search_without_query_returns_all_owner_files() {
        // Cas du dashboard : liste sans terme de recherche (q null). Le parametre null ne doit pas
        // casser le type du lower(...) cote PostgreSQL (bug lower(bytea) sans prepared statement serveur).
        fileRepo.save(pending("rapport-intervention.pdf", null));
        fileRepo.save(pending("photo.zip", null));
        PageResult<FileRecord> page = fileRepo.search(FileQuery.of(owner, null, null, 0, 20));
        assertThat(page.items()).hasSize(2);
        assertThat(page.totalElements()).isEqualTo(2);
    }

    @Test
    void count_by_owner_groups_statuses() {
        FileRecord clean = pending("c.txt", null);
        clean.markScanning(now);
        clean.markClean(ScanVerdict.clean("clamav", now), now);
        fileRepo.save(clean);
        fileRepo.save(pending("p.txt", null));

        StatusCounts counts = fileRepo.countByOwner(owner);
        assertThat(counts.total()).isEqualTo(2);
        assertThat(counts.clean()).isEqualTo(1);
        assertThat(counts.pending()).isEqualTo(1);
    }

    @Test
    void find_by_batch_and_stale_pending() {
        UUID batchId = UUID.randomUUID();
        fileRepo.save(pending("b1.txt", batchId));
        fileRepo.save(pending("b2.txt", batchId));
        assertThat(fileRepo.findByBatchAndOwner(batchId, owner)).hasSize(2);
        assertThat(fileRepo.findByStatusOlderThan(FileStatus.PENDING, now.plus(Duration.ofHours(1)))).hasSize(2);
    }

    @Test
    void api_client_save_and_resolve_by_hash() {
        ApiClient client = new ApiClient(UUID.randomUUID(), "Batir SA", "hash-123", true, now);
        apiClientRepo.save(client);
        assertThat(apiClientRepo.findByApiKeyHash("hash-123")).isPresent();
        assertThat(apiClientRepo.findByApiKeyHash("unknown")).isEmpty();
    }
}
