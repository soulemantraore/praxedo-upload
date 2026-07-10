package com.praxedo.upload.application;

import com.praxedo.upload.domain.file.FileRecord;
import com.praxedo.upload.domain.file.exceptions.DownloadNotAllowedException;
import com.praxedo.upload.domain.file.exceptions.FileNotFoundException;
import com.praxedo.upload.domain.port.FileMetadataRepository;
import com.praxedo.upload.domain.port.FileStorage;
import com.praxedo.upload.infrastructure.config.StorageProperties;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.UUID;

@Service
public class FileDownloadService {

    private final FileMetadataRepository repository;
    private final FileStorage storage;
    private final StorageProperties properties;

    public FileDownloadService(FileMetadataRepository repository, FileStorage storage, StorageProperties properties) {
        this.repository = repository;
        this.storage = storage;
        this.properties = properties;
    }

    /** Applique l'invariant : URL de telechargement emise uniquement si CLEAN. */
    public URI requestDownload(UUID ownerId, UUID fileId) {
        FileRecord file = repository.findByIdAndOwner(fileId, ownerId)
            .orElseThrow(() -> new FileNotFoundException(fileId));
        if (!file.isDownloadable()) {
            throw new DownloadNotAllowedException(file.status());
        }
        return storage.createDownloadUrl(file.storageKey(), properties.downloadUrlTtl());
    }
}
