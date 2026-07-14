package com.praxedo.upload.infrastructure.storage.gcs;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.praxedo.upload.domain.port.FileStorage;
import com.praxedo.upload.infrastructure.config.StorageProperties;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Teste NOTRE logique de wiring GCS (options de signature, BlobId), en mockant le client Storage. */
class GcsFileStorageTest {

    private final Storage storage = mock(Storage.class);
    private final StorageProperties props = new StorageProperties(
        new StorageProperties.Local("x"), Duration.ofMinutes(15), Duration.ofMinutes(5), "x");
    private final GcsFileStorage gcs = new GcsFileStorage(storage, "my-bucket", props);

    @Test
    void create_upload_target_signs_a_put_url() throws Exception {
        java.net.URL signed = URI.create("https://storage.googleapis.com/my-bucket/k?sig=abc").toURL();
        when(storage.signUrl(any(BlobInfo.class), eq(900L), eq(TimeUnit.SECONDS),
            any(Storage.SignUrlOption.class), any(Storage.SignUrlOption.class))).thenReturn(signed);

        FileStorage.UploadTarget target = gcs.createUploadTarget("owner/k.txt", "text/plain", 10);

        assertThat(target.url().toString()).contains("my-bucket");
        verify(storage).signUrl(any(BlobInfo.class), eq(900L), eq(TimeUnit.SECONDS),
            any(Storage.SignUrlOption.class), any(Storage.SignUrlOption.class));
    }

    @Test
    void create_download_url_signs_a_get_url() throws Exception {
        java.net.URL signed = URI.create("https://storage.googleapis.com/my-bucket/k?sig=xyz").toURL();
        when(storage.signUrl(any(BlobInfo.class), eq(300L), eq(TimeUnit.SECONDS),
            any(Storage.SignUrlOption.class), any(Storage.SignUrlOption.class))).thenReturn(signed);

        URI url = gcs.createDownloadUrl("owner/k.txt", Duration.ofMinutes(5));

        assertThat(url.toString()).contains("my-bucket");
    }

    @Test
    void delete_removes_the_blob() {
        gcs.delete("owner/k.txt");
        verify(storage).delete(BlobId.of("my-bucket", "owner/k.txt"));
    }

    @Test
    void exists_reflects_blob_presence() {
        Blob blob = mock(Blob.class);
        when(blob.exists()).thenReturn(true);
        when(storage.get(BlobId.of("my-bucket", "owner/k.txt"))).thenReturn(blob);
        when(storage.get(BlobId.of("my-bucket", "missing"))).thenReturn(null);

        assertThat(gcs.exists("owner/k.txt")).isTrue();
        assertThat(gcs.exists("missing")).isFalse();
    }
}
