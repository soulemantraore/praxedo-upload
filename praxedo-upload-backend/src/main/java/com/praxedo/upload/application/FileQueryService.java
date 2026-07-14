package com.praxedo.upload.application;

import com.praxedo.upload.application.dto.FileViews.BatchView;
import com.praxedo.upload.application.dto.FileViews.FileView;
import com.praxedo.upload.domain.file.FileQuery;
import com.praxedo.upload.domain.file.FileRecord;
import com.praxedo.upload.domain.file.FileStatus;
import com.praxedo.upload.domain.file.PageResult;
import com.praxedo.upload.domain.file.StatusCounts;
import com.praxedo.upload.domain.file.exceptions.FileNotFoundException;
import com.praxedo.upload.domain.port.FileMetadataRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class FileQueryService {

    private final FileMetadataRepository repository;

    public FileQueryService(FileMetadataRepository repository) {
        this.repository = repository;
    }

    public PageResult<FileView> list(FileQuery query) {
        PageResult<FileRecord> page = repository.search(query);
        List<FileView> views = page.items().stream().map(FileView::of).toList();
        return new PageResult<>(views, page.page(), page.size(), page.totalElements());
    }

    public StatusCounts stats(UUID ownerId) {
        return repository.countByOwner(ownerId);
    }

    public FileView getFile(UUID ownerId, UUID fileId) {
        return repository.findByIdAndOwner(fileId, ownerId).map(FileView::of)
            .orElseThrow(() -> new FileNotFoundException(fileId));
    }

    public BatchView getBatch(UUID ownerId, UUID batchId) {
        List<FileRecord> files = repository.findByBatchAndOwner(batchId, ownerId);
        if (files.isEmpty()) {
            throw new FileNotFoundException(batchId);
        }
        List<FileView> items = files.stream().map(FileView::of).toList();
        long clean = files.stream().filter(f -> f.status() == FileStatus.CLEAN).count();
        long scanning = files.stream().filter(f -> f.status() == FileStatus.SCANNING).count();
        long pending = files.stream().filter(f -> f.status() == FileStatus.PENDING).count();
        long blocked = files.stream().filter(f -> f.status() == FileStatus.INFECTED).count();
        return new BatchView(batchId, items, new StatusCounts(files.size(), clean, scanning, pending, blocked));
    }
}
