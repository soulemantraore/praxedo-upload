package com.praxedo.upload.application;

import com.praxedo.upload.domain.models.*;
import com.praxedo.upload.domain.exceptions.DownloadNotAllowedException;
import com.praxedo.upload.domain.exceptions.FileNotFoundException;
import com.praxedo.upload.infrastructure.config.StorageProperties;
import com.praxedo.upload.infrastructure.persistence.inmemory.InMemoryFileMetadataRepository;
import com.praxedo.upload.infrastructure.storage.local.LocalFileStorage;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileDownloadServiceTest {

    private final InMemoryFileMetadataRepository repo = new InMemoryFileMetadataRepository();
    private final LocalFileStorage storage = new LocalFileStorage("/tmp/praxedo-dl-test", "http://localhost:8080");
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-10T10:00:00Z"), ZoneOffset.UTC);
    private final StorageProperties props = new StorageProperties(
        new StorageProperties.Local("/tmp/praxedo-dl-test"), Duration.ofMinutes(15), Duration.ofMinutes(5), "http://localhost:8080");
    private final FileDownloadService service = new FileDownloadService(repo, storage, props);
    private final UUID owner = UUID.randomUUID();

    private FileRecord persisted(FileStatus status) {
        UUID id = UUID.randomUUID();
        FileRecord f = FileRecord.pending(id, owner, null, "a.txt", "text/plain", 3, owner + "/" + id + "/a.txt", clock.instant());
        if (status != FileStatus.PENDING) {
            f.markScanning(clock.instant());
        }
        if (status == FileStatus.CLEAN) {
            f.markClean(ScanVerdict.clean("fake", clock.instant()), clock.instant());
        }
        if (status == FileStatus.INFECTED) {
            f.markInfected(ScanVerdict.infected("fake", "Eicar", clock.instant()), clock.instant());
        }
        repo.save(f);
        return f;
    }

    @Test
    void clean_file_yields_a_download_url() {
        FileRecord f = persisted(FileStatus.CLEAN);
        assertThat(service.requestDownload(owner, f.id()).toString()).contains("/api/_local/download");
    }

    @Test
    void non_clean_file_is_refused() {
        FileRecord f = persisted(FileStatus.INFECTED);
        assertThatThrownBy(() -> service.requestDownload(owner, f.id()))
            .isInstanceOf(DownloadNotAllowedException.class);
    }

    @Test
    void other_owner_cannot_download() {
        FileRecord f = persisted(FileStatus.CLEAN);
        assertThatThrownBy(() -> service.requestDownload(UUID.randomUUID(), f.id()))
            .isInstanceOf(FileNotFoundException.class);
    }
}
