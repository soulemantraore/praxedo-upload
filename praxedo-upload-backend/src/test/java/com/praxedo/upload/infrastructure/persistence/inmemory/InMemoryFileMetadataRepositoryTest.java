package com.praxedo.upload.infrastructure.persistence.inmemory;

import com.praxedo.upload.domain.models.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryFileMetadataRepositoryTest {

    private final InMemoryFileMetadataRepository repo = new InMemoryFileMetadataRepository();
    private final UUID owner = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-07-10T10:00:00Z");

    private FileRecord file(String name, FileStatus status) {
        FileRecord f = FileRecord.pending(UUID.randomUUID(), owner, null, name, "text/plain", 10, "k/" + name, now);
        if (status == FileStatus.SCANNING || status == FileStatus.CLEAN) {
            f.markScanning(now);
        }
        if (status == FileStatus.CLEAN) {
            f.markClean(ScanVerdict.clean("fake", now), now);
        }
        return f;
    }

    @Test
    void save_then_find_by_owner() {
        FileRecord f = file("a.txt", FileStatus.PENDING);
        repo.save(f);
        assertThat(repo.findByIdAndOwner(f.id(), owner)).isPresent();
        assertThat(repo.findByIdAndOwner(f.id(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void search_filters_by_owner_and_status_and_query() {
        repo.save(file("rapport.txt", FileStatus.CLEAN));
        repo.save(file("photo.txt", FileStatus.PENDING));
        FileQuery q = FileQuery.of(owner, "rap", null, 0, 20);
        PageResult<FileRecord> page = repo.search(q);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).filename()).isEqualTo("rapport.txt");
    }

    @Test
    void count_by_owner_groups_statuses() {
        repo.save(file("a.txt", FileStatus.CLEAN));
        repo.save(file("b.txt", FileStatus.PENDING));
        StatusCounts c = repo.countByOwner(owner);
        assertThat(c.total()).isEqualTo(2);
        assertThat(c.clean()).isEqualTo(1);
        assertThat(c.pending()).isEqualTo(1);
    }
}
