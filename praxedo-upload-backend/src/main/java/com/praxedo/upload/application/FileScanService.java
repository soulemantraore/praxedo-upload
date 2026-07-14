package com.praxedo.upload.application;

import com.praxedo.upload.domain.models.FileRecord;
import com.praxedo.upload.domain.models.ScanVerdict;
import com.praxedo.upload.domain.port.AntivirusScanner;
import com.praxedo.upload.domain.port.FileMetadataRepository;
import com.praxedo.upload.domain.port.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
            log.warn("scan demande pour un fichier inconnu: {}", fileId);
            return;
        }
        Instant now = clock.instant();
        file.markScanning(now);
        repository.save(file);
        try {
            // Le scanner accede lui-meme aux octets a partir de la storageKey
            // (en gcp : appel HTTP a un scanner externe qui lit GCS).
            ScanVerdict verdict = scanner.scan(file.storageKey(), clock.instant());
            if (verdict.infected()) {
                file.markInfected(verdict, clock.instant());
                repository.save(file);
                storage.delete(file.storageKey());
                log.info("fichier {} INFECTED ({})", fileId, verdict.threatName());
            } else {
                file.markClean(verdict, clock.instant());
                repository.save(file);
                log.info("fichier {} CLEAN", fileId);
            }
        } catch (AntivirusScanner.ScanException e) {
            file.markScanFailed(clock.instant());
            repository.save(file);
            log.error("echec technique du scan pour {} : {}", fileId, e.getMessage());
        }
    }
}
