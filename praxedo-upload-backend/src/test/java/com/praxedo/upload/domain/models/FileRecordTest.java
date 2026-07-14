package com.praxedo.upload.domain.models;

import com.praxedo.upload.domain.exceptions.IllegalFileTransitionException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
