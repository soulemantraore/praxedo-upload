package com.praxedo.upload.infrastructure.scan.local;

import com.praxedo.upload.domain.file.ScanVerdict;
import com.praxedo.upload.infrastructure.storage.local.LocalFileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FakeAntivirusScannerTest {

    private final Instant now = Instant.parse("2026-07-10T10:00:00Z");

    // Standard EICAR antivirus test string (harmless).
    private static final String EICAR =
        "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

    private ScanVerdict scanContent(Path dir, String key, String body) throws Exception {
        LocalFileStorage storage = new LocalFileStorage(dir.toString(), "http://localhost:8080");
        storage.write(key, new ByteArrayInputStream(body.getBytes()));
        return new FakeAntivirusScanner(storage).scan(key, now);
    }

    @Test
    void clean_content_is_clean(@TempDir Path dir) throws Exception {
        ScanVerdict v = scanContent(dir, "owner/id/ok.txt", "bonjour");
        assertThat(v.infected()).isFalse();
    }

    @Test
    void eicar_content_is_infected(@TempDir Path dir) throws Exception {
        ScanVerdict v = scanContent(dir, "owner/id/virus.txt", EICAR);
        assertThat(v.infected()).isTrue();
        assertThat(v.threatName()).contains("Eicar");
    }
}
