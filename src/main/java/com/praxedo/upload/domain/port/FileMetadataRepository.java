package com.praxedo.upload.domain.port;

import com.praxedo.upload.domain.file.FileQuery;
import com.praxedo.upload.domain.file.FileRecord;
import com.praxedo.upload.domain.file.FileStatus;
import com.praxedo.upload.domain.file.PageResult;
import com.praxedo.upload.domain.file.StatusCounts;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port : persistance des metadonnees et du statut des fichiers.
 * Adapter par defaut = JPA/Postgres. Changer de moteur SQL = changer l'URL JDBC ;
 * changer de paradigme = nouvel adapter.
 */
public interface FileMetadataRepository {

    void save(FileRecord file);

    /** Acces interne (worker), non scope owner. */
    Optional<FileRecord> findById(UUID id);

    /** Resolution par cle de stockage (utilisee par la notification GCS -> scan). */
    Optional<FileRecord> findByStorageKey(String storageKey);

    /** Acces owner-scope : ne renvoie le fichier que s'il appartient a l'owner. */
    Optional<FileRecord> findByIdAndOwner(UUID id, UUID ownerId);

    PageResult<FileRecord> search(FileQuery query);

    List<FileRecord> findByBatchAndOwner(UUID batchId, UUID ownerId);

    StatusCounts countByOwner(UUID ownerId);

    /** Pour la reconciliation : fichiers dans un statut donne, non modifies depuis un seuil. */
    List<FileRecord> findByStatusOlderThan(FileStatus status, Instant threshold);
}
