package com.praxedo.upload.application;

import com.praxedo.upload.application.dto.UploadCommands.*;
import com.praxedo.upload.domain.models.FileStatus;
import com.praxedo.upload.domain.port.IdGenerator;
import com.praxedo.upload.infrastructure.persistence.inmemory.InMemoryFileMetadataRepository;
import com.praxedo.upload.infrastructure.storage.local.LocalFileStorage;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FileUploadServiceTest {

    private final InMemoryFileMetadataRepository repo = new InMemoryFileMetadataRepository();
    private final LocalFileStorage storage = new LocalFileStorage("/tmp/praxedo-upl-test", "http://localhost:8080");
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-10T10:00:00Z"), ZoneOffset.UTC);
    private int seq = 0;
    private final IdGenerator ids = () -> UUID.nameUUIDFromBytes(("id" + seq++).getBytes());
    private final FileUploadService service = new FileUploadService(repo, storage, ids, clock);
    private final UUID owner = UUID.randomUUID();

    @Test
    void register_upload_creates_pending_and_returns_upload_url() {
        var result = service.registerUpload(owner, new RegisterUploadCommand("a.pdf", "application/pdf", 100L));
        assertThat(result.status()).isEqualTo(FileStatus.PENDING);
        assertThat(result.uploadUrl().toString()).contains("/api/_local/upload");
        assertThat(repo.findByIdAndOwner(result.id(), owner)).isPresent();
    }

    @Test
    void storage_key_prefixes_owner_and_file_segments() {
        var result = service.registerUpload(owner, new RegisterUploadCommand("a.pdf", "application/pdf", 100L));
        String key = repo.findByIdAndOwner(result.id(), owner).orElseThrow().storageKey();
        assertThat(key).isEqualTo("client_" + owner + "/file_" + result.id() + "/a.pdf");
    }

    @Test
    void register_batch_shares_one_batch_id() {
        var result = service.registerBatch(owner, new RegisterBatchCommand(List.of(
            new RegisterUploadCommand("a.pdf", "application/pdf", 100L),
            new RegisterUploadCommand("b.pdf", "application/pdf", 200L))));
        assertThat(result.items()).hasSize(2);
        UUID batchId = result.batchId();
        result.items().forEach(i ->
            assertThat(repo.findById(i.id()).orElseThrow().batchId()).isEqualTo(batchId));
    }
}
