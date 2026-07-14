package com.praxedo.upload.infrastructure.persistence.jpa.adapters;

import com.praxedo.upload.domain.models.FileQuery;
import com.praxedo.upload.domain.models.FileRecord;
import com.praxedo.upload.domain.models.FileStatus;
import com.praxedo.upload.domain.models.PageResult;
import com.praxedo.upload.domain.models.StatusCounts;
import com.praxedo.upload.domain.port.FileMetadataRepository;
import com.praxedo.upload.infrastructure.persistence.jpa.entities.FileEntity;
import com.praxedo.upload.infrastructure.persistence.jpa.repositories.FileJpaRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adapter par defaut du port FileMetadataRepository : JPA / Postgres (Supabase, profil gcp). */
@Repository
@Profile("gcp")
public class JpaFileMetadataRepository implements FileMetadataRepository {

    private final FileJpaRepository jpa;

    public JpaFileMetadataRepository(FileJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(FileRecord file) {
        jpa.save(FileEntity.fromDomain(file));
    }

    @Override
    public Optional<FileRecord> findById(UUID id) {
        return jpa.findById(id).map(FileEntity::toDomain);
    }

    @Override
    public Optional<FileRecord> findByStorageKey(String storageKey) {
        return jpa.findByStorageKey(storageKey).map(FileEntity::toDomain);
    }

    @Override
    public Optional<FileRecord> findByIdAndOwner(UUID id, UUID ownerId) {
        return jpa.findByIdAndOwnerId(id, ownerId).map(FileEntity::toDomain);
    }

    @Override
    public PageResult<FileRecord> search(FileQuery query) {
        Pageable pageable = PageRequest.of(query.page(), query.size(), Sort.by("createdAt").descending());
        Page<FileEntity> page = jpa.search(query.ownerId(), query.status(), query.q(), pageable);
        List<FileRecord> items = page.getContent().stream().map(FileEntity::toDomain).toList();
        return new PageResult<>(items, query.page(), query.size(), page.getTotalElements());
    }

    @Override
    public List<FileRecord> findByBatchAndOwner(UUID batchId, UUID ownerId) {
        return jpa.findByBatchIdAndOwnerIdOrderByCreatedAt(batchId, ownerId).stream()
            .map(FileEntity::toDomain).toList();
    }

    @Override
    public StatusCounts countByOwner(UUID ownerId) {
        return new StatusCounts(
            jpa.countByOwnerId(ownerId),
            jpa.countByOwnerIdAndStatus(ownerId, FileStatus.CLEAN),
            jpa.countByOwnerIdAndStatus(ownerId, FileStatus.SCANNING),
            jpa.countByOwnerIdAndStatus(ownerId, FileStatus.PENDING),
            jpa.countByOwnerIdAndStatus(ownerId, FileStatus.INFECTED));
    }

    @Override
    public List<FileRecord> findByStatusOlderThan(FileStatus status, Instant threshold) {
        return jpa.findByStatusAndUpdatedAtBefore(status, threshold).stream()
            .map(FileEntity::toDomain).toList();
    }
}
