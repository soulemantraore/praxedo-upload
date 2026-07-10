package com.praxedo.upload.infrastructure.scan;

import com.praxedo.upload.domain.file.ScanVerdict;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FakeAntivirusScannerTest {

    private final FakeAntivirusScanner scanner = new FakeAntivirusScanner();
    private final Instant now = Instant.parse("2026-07-10T10:00:00Z");

    // Standard EICAR antivirus test string (harmless).
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
