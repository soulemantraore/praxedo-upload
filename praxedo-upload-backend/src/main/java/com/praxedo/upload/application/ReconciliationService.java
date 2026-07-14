package com.praxedo.upload.application;

import com.praxedo.upload.domain.models.FileRecord;
import com.praxedo.upload.domain.models.FileStatus;
import com.praxedo.upload.domain.port.FileMetadataRepository;
import org.springframework.beans.factory.annotation.Value;
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
                                 @Value("${scan.pending-ttl:PT1H}") Duration pendingTtl) {
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
