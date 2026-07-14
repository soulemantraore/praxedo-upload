package com.praxedo.upload.domain.models;

import com.praxedo.upload.domain.exceptions.IllegalFileTransitionException;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entite du domaine : un fichier et son etat dans le cycle de vie.
 * POJO (Lombok = build-time uniquement, aucune dependance runtime) : la persistance a son propre modele (adapter JPA).
 * Les transitions passent toutes par {@link #transitionTo} qui applique la machine a etats.
 * La construction se fait via le {@code @Builder}, encapsule par les fabriques {@link #pending} / {@link #rehydrate}
 * qui garantissent des etats initiaux coherents. Accesseurs "record-like" generes par Lombok ({@code id()}, {@code status()}, ...).
 */
@Getter
@Accessors(fluent = true)
public class FileRecord {

    private final UUID id;
    private final UUID ownerId;
    private final UUID batchId; // nullable
    private final String filename;
    private final String contentType;
    private final long sizeBytes;
    private final String storageKey;
    private FileStatus status;
    private ScanVerdict scanVerdict; // nullable
    private int scanAttempts;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant scannedAt; // nullable

    @Builder
    private FileRecord(UUID id, UUID ownerId, UUID batchId, String filename, String contentType,
                       long sizeBytes, String storageKey, FileStatus status, ScanVerdict scanVerdict,
                       int scanAttempts, Instant createdAt, Instant updatedAt, Instant scannedAt) {
        this.id = Objects.requireNonNull(id);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.batchId = batchId;
        this.filename = Objects.requireNonNull(filename);
        this.contentType = Objects.requireNonNull(contentType);
        this.sizeBytes = sizeBytes;
        this.storageKey = Objects.requireNonNull(storageKey);
        this.status = Objects.requireNonNull(status);
        this.scanVerdict = scanVerdict;
        this.scanAttempts = scanAttempts;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.scannedAt = scannedAt;
    }

    /** Cree un fichier fraichement enregistre (en attente de scan). */
    public static FileRecord pending(UUID id, UUID ownerId, UUID batchId, String filename,
                                     String contentType, long sizeBytes, String storageKey, Instant now) {
        return FileRecord.builder()
            .id(id)
            .ownerId(ownerId)
            .batchId(batchId)
            .filename(filename)
            .contentType(contentType)
            .sizeBytes(sizeBytes)
            .storageKey(storageKey)
            .status(FileStatus.PENDING)
            .scanAttempts(0)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }

    /** Reconstruit un fichier depuis la persistance (adapter JPA, jalon ulterieur). */
    public static FileRecord rehydrate(UUID id, UUID ownerId, UUID batchId, String filename,
                                       String contentType, long sizeBytes, String storageKey,
                                       FileStatus status, ScanVerdict scanVerdict, int scanAttempts,
                                       Instant createdAt, Instant updatedAt, Instant scannedAt) {
        return FileRecord.builder()
            .id(id)
            .ownerId(ownerId)
            .batchId(batchId)
            .filename(filename)
            .contentType(contentType)
            .sizeBytes(sizeBytes)
            .storageKey(storageKey)
            .status(status)
            .scanVerdict(scanVerdict)
            .scanAttempts(scanAttempts)
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .scannedAt(scannedAt)
            .build();
    }

    private void transitionTo(FileStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalFileTransitionException(status, target);
        }
        this.status = target;
        this.updatedAt = now;
    }

    public void markScanning(Instant now) {
        transitionTo(FileStatus.SCANNING, now);
        this.scanAttempts++;
    }

    public void markClean(ScanVerdict verdict, Instant now) {
        transitionTo(FileStatus.CLEAN, now);
        this.scanVerdict = verdict;
        this.scannedAt = now;
    }

    public void markInfected(ScanVerdict verdict, Instant now) {
        transitionTo(FileStatus.INFECTED, now);
        this.scanVerdict = verdict;
        this.scannedAt = now;
    }

    public void markScanFailed(Instant now) {
        transitionTo(FileStatus.SCAN_FAILED, now);
    }

    public void markExpired(Instant now) {
        transitionTo(FileStatus.EXPIRED, now);
    }

    public boolean isDownloadable() {
        return status.isDownloadable();
    }
}
