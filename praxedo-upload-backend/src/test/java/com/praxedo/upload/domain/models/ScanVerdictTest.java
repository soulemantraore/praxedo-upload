package com.praxedo.upload.domain.models;

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
