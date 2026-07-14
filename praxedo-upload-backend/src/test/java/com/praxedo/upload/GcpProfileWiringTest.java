package com.praxedo.upload;

import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.storage.Storage;
import com.praxedo.upload.domain.port.ApiClientRepository;
import com.praxedo.upload.domain.port.AntivirusScanner;
import com.praxedo.upload.domain.port.FileMetadataRepository;
import com.praxedo.upload.domain.port.FileStorage;
import com.praxedo.upload.domain.port.ScanQueue;
import com.praxedo.upload.infrastructure.persistence.jpa.adapters.JpaApiClientRepository;
import com.praxedo.upload.infrastructure.persistence.jpa.adapters.JpaFileMetadataRepository;
import com.praxedo.upload.infrastructure.scan.RemoteScannerClient;
import com.praxedo.upload.infrastructure.scan.PubSubScanQueue;
import com.praxedo.upload.infrastructure.storage.gcs.GcsFileStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifie que le profil "gcp" cable bien les 4 adapters REELS (et pas les doublures local/test).
 * Storage (GCS) et Publisher (Pub/Sub) sont mockes (pas de credentials en test) ; la base est un
 * vrai PostgreSQL (Testcontainers) pour que la couche JPA/Flyway se charge.
 */
@SpringBootTest
@ActiveProfiles("gcp")
@Testcontainers
class GcpProfileWiringTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Scanner distant : URL bidon + OIDC desactive (aucun appel reel ni ADC dans ce test de cablage).
        registry.add("scanner.base-url", () -> "http://localhost:65535");
        registry.add("scanner.oidc-enabled", () -> "false");
    }

    @MockBean
    Storage storage;
    @MockBean
    Publisher publisher;

    @Autowired
    FileStorage fileStorage;
    @Autowired
    ScanQueue scanQueue;
    @Autowired
    AntivirusScanner scanner;
    @Autowired
    FileMetadataRepository fileMetadataRepository;
    @Autowired
    ApiClientRepository apiClientRepository;

    @Test
    void gcp_profile_wires_the_real_adapters() {
        assertThat(fileStorage).isInstanceOf(GcsFileStorage.class);
        assertThat(scanQueue).isInstanceOf(PubSubScanQueue.class);
        assertThat(scanner).isInstanceOf(RemoteScannerClient.class);
        assertThat(fileMetadataRepository).isInstanceOf(JpaFileMetadataRepository.class);
        assertThat(apiClientRepository).isInstanceOf(JpaApiClientRepository.class);
    }
}
