package com.praxedo.upload.infrastructure.web.controllers;

import com.praxedo.upload.application.FileDownloadService;
import com.praxedo.upload.application.FileQueryService;
import com.praxedo.upload.application.FileUploadService;
import com.praxedo.upload.application.dto.FileViews.FileView;
import com.praxedo.upload.application.dto.UploadCommands.RegisterUploadCommand;
import com.praxedo.upload.application.dto.UploadCommands.UploadRegistration;
import com.praxedo.upload.domain.file.FileQuery;
import com.praxedo.upload.domain.file.FileStatus;
import com.praxedo.upload.domain.file.PageResult;
import com.praxedo.upload.domain.file.StatusCounts;
import com.praxedo.upload.domain.port.ScanQueue;
import com.praxedo.upload.infrastructure.web.dto.UploadRequests.RegisterFileRequest;
import com.praxedo.upload.infrastructure.web.helpers.AuthenticatedClient;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public class FilesController {

    private final FileUploadService uploadService;
    private final FileQueryService queryService;
    private final FileDownloadService downloadService;
    private final ScanQueue scanQueue;

    public FilesController(FileUploadService uploadService, FileQueryService queryService,
                           FileDownloadService downloadService, ScanQueue scanQueue) {
        this.uploadService = uploadService;
        this.queryService = queryService;
        this.downloadService = downloadService;
        this.scanQueue = scanQueue;
    }

    @PostMapping
    public ResponseEntity<UploadRegistration> register(@AuthenticationPrincipal AuthenticatedClient client,
                                                        @Valid @RequestBody RegisterFileRequest req) {
        UploadRegistration reg = uploadService.registerUpload(client.ownerId(),
            new RegisterUploadCommand(req.filename(), req.contentType(), req.size()));
        return ResponseEntity.status(HttpStatus.CREATED).body(reg);
    }

    @GetMapping
    public PageResult<FileView> list(@AuthenticationPrincipal AuthenticatedClient client,
                                     @RequestParam(required = false) String q,
                                     @RequestParam(required = false) FileStatus status,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return queryService.list(FileQuery.of(client.ownerId(), q, status, page, size));
    }

    @GetMapping("/stats")
    public StatusCounts stats(@AuthenticationPrincipal AuthenticatedClient client) {
        return queryService.stats(client.ownerId());
    }

    @GetMapping("/{id}")
    public FileView get(@AuthenticationPrincipal AuthenticatedClient client, @PathVariable UUID id) {
        return queryService.getFile(client.ownerId(), id);
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<Void> download(@AuthenticationPrincipal AuthenticatedClient client, @PathVariable UUID id) {
        URI url = downloadService.requestDownload(client.ownerId(), id);
        return ResponseEntity.status(HttpStatus.FOUND).location(url).build();
    }

    @PostMapping("/{id}/rescan")
    public ResponseEntity<Void> rescan(@AuthenticationPrincipal AuthenticatedClient client, @PathVariable UUID id) {
        // verifie la propriete (404 si autre owner) avant de (re)mettre en file
        queryService.getFile(client.ownerId(), id);
        scanQueue.enqueue(new ScanQueue.ScanRequest(id));
        return ResponseEntity.accepted().build();
    }
}
