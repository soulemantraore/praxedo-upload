package com.praxedo.upload.infrastructure.persistence.inmemory;

import com.praxedo.upload.domain.models.*;
import com.praxedo.upload.domain.port.FileMetadataRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
@Profile({"local", "test"})
public class InMemoryFileMetadataRepository implements FileMetadataRepository {

    private final Map<UUID, FileRecord> store = new ConcurrentHashMap<>();

    @Override
    public void save(FileRecord file) {
        store.put(file.id(), file);
    }

    @Override
    public Optional<FileRecord> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<FileRecord> findByStorageKey(String storageKey) {
        return store.values().stream().filter(f -> f.storageKey().equals(storageKey)).findFirst();
    }

    @Override
    public Optional<FileRecord> findByIdAndOwner(UUID id, UUID ownerId) {
        return findById(id).filter(f -> f.ownerId().equals(ownerId));
    }

    @Override
    public PageResult<FileRecord> search(FileQuery query) {
        List<FileRecord> matched = store.values().stream()
            .filter(f -> f.ownerId().equals(query.ownerId()))
            .filter(f -> query.status() == null || f.status() == query.status())
            .filter(f -> query.q() == null || f.filename().toLowerCase().contains(query.q().toLowerCase()))
            .sorted(Comparator.comparing(FileRecord::createdAt).reversed())
            .collect(Collectors.toList());
        int from = Math.min(query.page() * query.size(), matched.size());
        int to = Math.min(from + query.size(), matched.size());
        return PageResult.of(matched.subList(from, to), query.page(), query.size(), matched.size());
    }

    @Override
    public List<FileRecord> findByBatchAndOwner(UUID batchId, UUID ownerId) {
        return store.values().stream()
            .filter(f -> ownerId.equals(f.ownerId()) && batchId.equals(f.batchId()))
            .sorted(Comparator.comparing(FileRecord::createdAt))
            .collect(Collectors.toList());
    }

    @Override
    public StatusCounts countByOwner(UUID ownerId) {
        List<FileRecord> owned = store.values().stream()
            .filter(f -> f.ownerId().equals(ownerId)).toList();
        long total = owned.size();
        long clean = owned.stream().filter(f -> f.status() == FileStatus.CLEAN).count();
        long scanning = owned.stream().filter(f -> f.status() == FileStatus.SCANNING).count();
        long pending = owned.stream().filter(f -> f.status() == FileStatus.PENDING).count();
        long blocked = owned.stream().filter(f -> f.status() == FileStatus.INFECTED).count();
        return new StatusCounts(total, clean, scanning, pending, blocked);
    }

    @Override
    public List<FileRecord> findByStatusOlderThan(FileStatus status, Instant threshold) {
        return store.values().stream()
            .filter(f -> f.status() == status && f.updatedAt().isBefore(threshold))
            .collect(Collectors.toList());
    }
}
