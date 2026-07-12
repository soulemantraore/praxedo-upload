package com.praxedo.upload.infrastructure.storage.gcs;

import com.google.cloud.NoCredentials;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.praxedo.upload.infrastructure.config.StorageProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** IT : read/exists/delete contre un vrai serveur GCS-compatible (fake-gcs-server). Skippe sans Docker. */
@Testcontainers(disabledWithoutDocker = true)
class GcsFileStorageIntegrationTest {

    private static final String BUCKET = "praxedo-test";

    @Container
    static final GenericContainer<?> FAKE_GCS =
        new GenericContainer<>(DockerImageName.parse("fsouza/fake-gcs-server:latest"))
            .withExposedPorts(4443)
            .withCommand("-scheme", "http", "-host", "0.0.0.0", "-port", "4443")
            .waitingFor(Wait.forListeningPort());

    static Storage storage;

    @BeforeAll
    static void setup() {
        String endpoint = "http://" + FAKE_GCS.getHost() + ":" + FAKE_GCS.getMappedPort(4443);
        storage = StorageOptions.newBuilder()
            .setProjectId("test")
            .setHost(endpoint)
            .setCredentials(NoCredentials.getInstance())
            .build()
            .getService();
        storage.create(BucketInfo.of(BUCKET));
    }

    private GcsFileStorage gcs() {
        StorageProperties props = new StorageProperties(
            new StorageProperties.Local("x"), Duration.ofMinutes(15), Duration.ofMinutes(5), "x");
        return new GcsFileStorage(storage, BUCKET, props);
    }

    @Test
    void read_exists_delete_round_trip() throws Exception {
        String key = "owner/id/a.txt";
        storage.create(BlobInfo.newBuilder(BUCKET, key).build(), "hello gcs".getBytes());

        GcsFileStorage gcs = gcs();
        assertThat(gcs.exists(key)).isTrue();
        assertThat(new String(gcs.read(key).readAllBytes())).isEqualTo("hello gcs");
        gcs.delete(key);
        assertThat(gcs.exists(key)).isFalse();
    }
}
