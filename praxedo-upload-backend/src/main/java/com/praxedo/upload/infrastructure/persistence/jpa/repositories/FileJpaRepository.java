package com.praxedo.upload.infrastructure.persistence.jpa.repositories;

import com.praxedo.upload.domain.models.FileStatus;
import com.praxedo.upload.infrastructure.persistence.jpa.entities.FileEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository Spring Data JPA des fichiers (utilise par l'adapter JpaFileMetadataRepository). */
public interface FileJpaRepository extends JpaRepository<FileEntity, UUID> {

    Optional<FileEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<FileEntity> findByStorageKey(String storageKey);

    List<FileEntity> findByBatchIdAndOwnerIdOrderByCreatedAt(UUID batchId, UUID ownerId);

    List<FileEntity> findByStatusAndUpdatedAtBefore(FileStatus status, Instant threshold);

    long countByOwnerId(UUID ownerId);

    long countByOwnerIdAndStatus(UUID ownerId, FileStatus status);

    @Query("select f from FileEntity f where f.ownerId = :ownerId "
        + "and (:status is null or f.status = :status) "
        + "and (:q is null or lower(f.filename) like lower(concat('%', :q, '%')))")
    Page<FileEntity> search(@Param("ownerId") UUID ownerId,
                            @Param("status") FileStatus status,
                            @Param("q") String q,
                            Pageable pageable);
}
