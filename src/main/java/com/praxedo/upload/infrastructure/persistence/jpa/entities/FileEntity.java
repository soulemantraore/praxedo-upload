package com.praxedo.upload.infrastructure.persistence.jpa.entities;

import com.praxedo.upload.domain.file.FileRecord;
import com.praxedo.upload.domain.file.FileStatus;
import com.praxedo.upload.domain.file.ScanVerdict;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Modele de persistance JPA du fichier, DISTINCT du domaine (FileRecord reste un POJO sans annotation).
 * Le verdict de scan est aplati en colonnes ; le mapping se fait via {@link #fromDomain}/{@link #toDomain}.
 */
@Entity
@Table(name = "file")
public class FileEntity {

    @Id
    private UUID id;
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;
    @Column(name = "batch_id")
    private UUID batchId;
    @Column(nullable = false)
    private String filename;
    @Column(name = "content_type", nullable = false)
    private String contentType;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    @Column(name = "storage_key", nullable = false)
    private String storageKey;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FileStatus status;
    @Column(name = "scan_attempts", nullable = false)
    private int scanAttempts;
    @Column(name = "scan_engine")
    private String scanEngine;
    @Column(name = "scan_infected")
    private Boolean scanInfected;
    @Column(name = "threat_name")
    private String threatName;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "scanned_at")
    private Instant scannedAt;

    protected FileEntity() {
    }

    public static FileEntity fromDomain(FileRecord f) {
        FileEntity e = new FileEntity();
        e.id = f.id();
        e.ownerId = f.ownerId();
        e.batchId = f.batchId();
        e.filename = f.filename();
        e.contentType = f.contentType();
        e.sizeBytes = f.sizeBytes();
        e.storageKey = f.storageKey();
        e.status = f.status();
        e.scanAttempts = f.scanAttempts();
        ScanVerdict v = f.scanVerdict();
        if (v != null) {
            e.scanEngine = v.engine();
            e.scanInfected = v.infected();
            e.threatName = v.threatName();
        }
        e.createdAt = f.createdAt();
        e.updatedAt = f.updatedAt();
        e.scannedAt = f.scannedAt();
        return e;
    }

    public FileRecord toDomain() {
        ScanVerdict verdict = scanEngine == null ? null
            : new ScanVerdict(Boolean.TRUE.equals(scanInfected), scanEngine, threatName, scannedAt);
        return FileRecord.rehydrate(id, ownerId, batchId, filename, contentType, sizeBytes, storageKey,
            status, verdict, scanAttempts, createdAt, updatedAt, scannedAt);
    }
}
