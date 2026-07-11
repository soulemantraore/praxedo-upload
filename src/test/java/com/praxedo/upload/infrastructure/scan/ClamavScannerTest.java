package com.praxedo.upload.infrastructure.scan;

import com.praxedo.upload.domain.file.ScanVerdict;
import com.praxedo.upload.infrastructure.scan.clamav.ClamdClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test d'integration : vrai ClamAV via Testcontainers. Verifie la delegation reelle du scan
 * (contenu sain -> CLEAN, signature EICAR -> INFECTED). Skippe si Docker absent.
 */
@Testcontainers(disabledWithoutDocker = true)
class ClamavScannerTest {

    private static final String EICAR =
        "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";
    private static final Instant NOW = Instant.parse("2026-07-12T10:00:00Z");

    // Variante Debian : multi-arch (inclut arm64), contrairement a l'image Alpine par defaut (amd64 seulement).
    @Container
    static final GenericContainer<?> CLAMAV =
        new GenericContainer<>(DockerImageName.parse("clamav/clamav-debian:latest"))
            .withExposedPorts(3310)
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(4)));

    private static String host;
    private static int port;

    @BeforeAll
    static void awaitClamdReady() throws InterruptedException {
        host = CLAMAV.getHost();
        port = CLAMAV.getMappedPort(3310);
        ClamdClient client = new ClamdClient(host, port);
        for (int i = 0; i < 90; i++) {
            if (client.ping()) {
                return;
            }
            Thread.sleep(2000);
        }
        fail("clamd n'a pas repondu au PING dans le delai imparti");
    }

    private ClamavScanner scanner() {
        return new ClamavScanner(host, port);
    }

    @Test
    void clean_content_is_clean() throws Exception {
        ScanVerdict v = scanner().scan(new ByteArrayInputStream("contenu parfaitement sain".getBytes()), NOW);
        assertThat(v.infected()).isFalse();
        assertThat(v.engine()).isEqualTo("clamav");
    }

    @Test
    void eicar_content_is_infected() throws Exception {
        ScanVerdict v = scanner().scan(new ByteArrayInputStream(EICAR.getBytes()), NOW);
        assertThat(v.infected()).isTrue();
        assertThat(v.threatName()).containsIgnoringCase("eicar");
    }
}
