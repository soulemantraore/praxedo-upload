package com.praxedo.upload.infrastructure.storage.gcs;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.praxedo.upload.domain.port.FileStorage;
import com.praxedo.upload.infrastructure.config.StorageProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.Channels;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Adapter reel du port FileStorage : Google Cloud Storage. Les octets ne transitent jamais par l'app :
 * upload/download se font via des URLs signees V4 (le client parle directement a GCS).
 * read/delete/exists servent au worker de scan. Actif en profil gcp.
 */
@Component
@Profile("gcp")
public class GcsFileStorage implements FileStorage {

    private final Storage storage;
    private final String bucket;
    private final Duration uploadTtl;
    private final Duration downloadTtl;

    public GcsFileStorage(Storage storage,
                          @Value("${storage.gcs.bucket}") String bucket,
                          StorageProperties properties) {
        this.storage = storage;
        this.bucket = bucket;
        this.uploadTtl = properties.uploadUrlTtl();
        this.downloadTtl = properties.downloadUrlTtl();
    }

    @Override
    public UploadTarget createUploadTarget(String storageKey, String contentType, long sizeBytes) {
        BlobInfo blobInfo = BlobInfo.newBuilder(bucket, storageKey).setContentType(contentType).build();
        return new UploadTarget(signedUrl(blobInfo, uploadTtl, HttpMethod.PUT), Instant.now().plus(uploadTtl));
    }

    @Override
    public URI createDownloadUrl(String storageKey, Duration ttl) {
        BlobInfo blobInfo = BlobInfo.newBuilder(bucket, storageKey).build();
        Duration effective = ttl != null ? ttl : downloadTtl;
        return signedUrl(blobInfo, effective, HttpMethod.GET);
    }

    private URI signedUrl(BlobInfo blobInfo, Duration ttl, HttpMethod method) {
        try {
            return storage.signUrl(blobInfo, ttl.toSeconds(), TimeUnit.SECONDS,
                Storage.SignUrlOption.httpMethod(method),
                Storage.SignUrlOption.withV4Signature()).toURI();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("URL signee GCS invalide", e);
        }
    }

    @Override
    public InputStream read(String storageKey) {
        Blob blob = storage.get(BlobId.of(bucket, storageKey));
        if (blob == null || !blob.exists()) {
            throw new IllegalStateException("objet GCS introuvable: " + storageKey);
        }
        return Channels.newInputStream(blob.reader());
    }

    @Override
    public void delete(String storageKey) {
        storage.delete(BlobId.of(bucket, storageKey));
    }

    @Override
    public boolean exists(String storageKey) {
        Blob blob = storage.get(BlobId.of(bucket, storageKey));
        return blob != null && blob.exists();
    }
}
