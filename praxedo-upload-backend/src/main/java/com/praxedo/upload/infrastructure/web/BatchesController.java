package com.praxedo.upload.infrastructure.web;

import com.praxedo.upload.application.FileQueryService;
import com.praxedo.upload.application.FileUploadService;
import com.praxedo.upload.application.dto.FileViews.BatchView;
import com.praxedo.upload.application.dto.UploadCommands.BatchRegistration;
import com.praxedo.upload.application.dto.UploadCommands.RegisterBatchCommand;
import com.praxedo.upload.application.dto.UploadCommands.RegisterUploadCommand;
import com.praxedo.upload.infrastructure.web.dto.UploadRequests.RegisterBatchRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/batches")
public class BatchesController {

    private final FileUploadService uploadService;
    private final FileQueryService queryService;

    public BatchesController(FileUploadService uploadService, FileQueryService queryService) {
        this.uploadService = uploadService;
        this.queryService = queryService;
    }

    @PostMapping
    public ResponseEntity<BatchRegistration> register(@AuthenticationPrincipal AuthenticatedClient client,
                                                       @Valid @RequestBody RegisterBatchRequest req) {
        var commands = req.files().stream()
            .map(f -> new RegisterUploadCommand(f.filename(), f.contentType(), f.size())).toList();
        BatchRegistration reg = uploadService.registerBatch(client.ownerId(), new RegisterBatchCommand(commands));
        return ResponseEntity.status(HttpStatus.CREATED).body(reg);
    }

    @GetMapping("/{batchId}")
    public BatchView get(@AuthenticationPrincipal AuthenticatedClient client, @PathVariable UUID batchId) {
        return queryService.getBatch(client.ownerId(), batchId);
    }
}
