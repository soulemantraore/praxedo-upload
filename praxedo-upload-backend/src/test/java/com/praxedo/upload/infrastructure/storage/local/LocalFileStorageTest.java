package com.praxedo.upload.infrastructure.storage.local;

import com.praxedo.upload.domain.port.FileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFileStorageTest {

    @Test
    void write_then_read_and_delete(@TempDir Path dir) throws Exception {
        LocalFileStorage storage = new LocalFileStorage(dir.toString(), "http://localhost:8080");
        storage.write("owner/a.txt", new ByteArrayInputStream("hello".getBytes()));
        assertThat(storage.exists("owner/a.txt")).isTrue();
        assertThat(new String(storage.read("owner/a.txt").readAllBytes())).isEqualTo("hello");
        storage.delete("owner/a.txt");
        assertThat(storage.exists("owner/a.txt")).isFalse();
    }

    @Test
    void upload_target_is_a_proxy_url() {
        LocalFileStorage storage = new LocalFileStorage("/tmp/praxedo-local-test", "http://localhost:8080");
        FileStorage.UploadTarget t = storage.createUploadTarget("owner/a.txt", "text/plain", 5);
        assertThat(t.url().toString()).contains("/api/_local/upload");
        assertThat(storage.createDownloadUrl("owner/a.txt", Duration.ofMinutes(5)).toString())
            .contains("/api/_local/download");
    }
}
