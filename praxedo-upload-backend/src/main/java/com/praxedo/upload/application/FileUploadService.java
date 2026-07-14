package com.praxedo.upload.application;

import com.praxedo.upload.application.dto.UploadCommands.*;
import com.praxedo.upload.domain.models.FileRecord;
import com.praxedo.upload.domain.port.FileMetadataRepository;
import com.praxedo.upload.domain.port.FileStorage;
import com.praxedo.upload.domain.port.IdGenerator;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FileUploadService {

    private final FileMetadataRepository repository;
    private final FileStorage storage;
    private final IdGenerator ids;
    private final Clock clock;

    public FileUploadService(FileMetadataRepository repository, FileStorage storage, IdGenerator ids, Clock clock) {
        this.repository = repository;
        this.storage = storage;
        this.ids = ids;
        this.clock = clock;
    }

    public UploadRegistration registerUpload(UUID ownerId, RegisterUploadCommand cmd) {
        return register(ownerId, cmd, null);
    }

    public BatchRegistration registerBatch(UUID ownerId, RegisterBatchCommand cmd) {
        UUID batchId = ids.newId();
        List<UploadRegistration> items = new ArrayList<>();
        for (RegisterUploadCommand file : cmd.files()) {
            items.add(register(ownerId, file, batchId));
        }
        return new BatchRegistration(batchId, items);
    }

    private UploadRegistration register(UUID ownerId, RegisterUploadCommand cmd, UUID batchId) {
        UUID id = ids.newId();
        Instant now = clock.instant();
        String storageKey = ownerId + "/" + id + "/" + cmd.filename();
        FileRecord record = FileRecord.pending(id, ownerId, batchId, cmd.filename(),
            cmd.contentType(), cmd.sizeBytes(), storageKey, now);
        repository.save(record);
        FileStorage.UploadTarget target = storage.createUploadTarget(storageKey, cmd.contentType(), cmd.sizeBytes());
        return new UploadRegistration(id, record.status(), target.url(), target.expiresAt());
    }
}
