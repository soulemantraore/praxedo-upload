package com.praxedo.upload.application;

import com.praxedo.upload.domain.file.*;
import com.praxedo.upload.domain.port.AntivirusScanner;
import com.praxedo.upload.infrastructure.persistence.inmemory.InMemoryFileMetadataRepository;
import com.praxedo.upload.infrastructure.scan.FakeAntivirusScanner;
import com.praxedo.upload.infrastructure.storage.local.LocalFileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FileScanServiceTest {

    private final InMemoryFileMetadataRepository repo = new InMemoryFileMetadataRepository();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-10T10:00:00Z"), ZoneOffset.UTC);
    private final UUID owner = UUID.randomUUID();
    private static final String EICAR =
        "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

    private FileScanService service(LocalFileStorage storage, AntivirusScanner scanner) {
        return new FileScanService(repo, storage, scanner, clock);
    }

    private FileRecord persistPending(LocalFileStorage storage, String name, String body) {
        UUID id = UUID.randomUUID();
        String key = owner + "/" + id + "/" + name;
        storage.write(key, new ByteArrayInputStream(body.getBytes()));
        FileRecord f = FileRecord.pending(id, owner, null, name, "text/plain", body.length(), key, clock.instant());
        repo.save(f);
        return f;
    }

    @Test
    void clean_file_becomes_clean(@TempDir Path dir) {
        LocalFileStorage storage = new LocalFileStorage(dir.toString(), "http://localhost:8080");
        FileRecord f = persistPending(storage, "ok.txt", "contenu sain");
        service(storage, new FakeAntivirusScanner(storage)).scan(f.id());
        assertThat(repo.findById(f.id()).orElseThrow().status()).isEqualTo(FileStatus.CLEAN);
    }

    @Test
    void infected_file_becomes_infected_and_bytes_deleted(@TempDir Path dir) {
        LocalFileStorage storage = new LocalFileStorage(dir.toString(), "http://localhost:8080");
        FileRecord f = persistPending(storage, "virus.txt", EICAR);
        service(storage, new FakeAntivirusScanner(storage)).scan(f.id());
        FileRecord after = repo.findById(f.id()).orElseThrow();
        assertThat(after.status()).isEqualTo(FileStatus.INFECTED);
        assertThat(storage.exists(f.storageKey())).isFalse();
    }

    @Test
    void technical_failure_marks_scan_failed(@TempDir Path dir) {
        LocalFileStorage storage = new LocalFileStorage(dir.toString(), "http://localhost:8080");
        FileRecord f = persistPending(storage, "x.txt", "data");
        AntivirusScanner failing = (key, at) -> { throw new AntivirusScanner.ScanException("moteur KO"); };
        service(storage, failing).scan(f.id());
        assertThat(repo.findById(f.id()).orElseThrow().status()).isEqualTo(FileStatus.SCAN_FAILED);
    }
}
