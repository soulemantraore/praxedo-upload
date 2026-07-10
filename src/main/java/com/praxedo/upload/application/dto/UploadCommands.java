package com.praxedo.upload.application.dto;

import com.praxedo.upload.domain.file.FileStatus;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class UploadCommands {

    private UploadCommands() {
    }

    public record RegisterUploadCommand(String filename, String contentType, long sizeBytes) {
    }

    public record RegisterBatchCommand(List<RegisterUploadCommand> files) {
    }

    public record UploadRegistration(UUID id, FileStatus status, URI uploadUrl, Instant uploadExpiresAt) {
    }

    public record BatchRegistration(UUID batchId, List<UploadRegistration> items) {
    }
}
