package com.praxedo.upload.application;

import com.praxedo.upload.domain.file.FileRecord;
import com.praxedo.upload.domain.file.FileStatus;
import com.praxedo.upload.infrastructure.persistence.inmemory.InMemoryFileMetadataRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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
