package com.praxedo.upload.domain.port;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;

/**
 * Port : stockage des octets. L'adapter par defaut (GCS) renvoie des URLs signees ;
 * l'adapter local renvoie des URLs proxy. Le domaine ne connait jamais le SDK sous-jacent.
 */
public interface FileStorage {

    /** Cible d'upload : URL ou le client PUT les octets, et son expiration. */
    record UploadTarget(URI url, Instant expiresAt) {}

    UploadTarget createUploadTarget(String storageKey, String contentType, long sizeBytes);

    /**
     * URL de telechargement. {@code downloadFilename} sert a forcer un telechargement avec ce nom
     * (Content-Disposition: attachment) cote stockage, indispensable en navigation cross-origin.
     */
    URI createDownloadUrl(String storageKey, String downloadFilename, Duration ttl);

    /** Lecture des octets (utilisee par le worker de scan). */
    InputStream read(String storageKey);

    void delete(String storageKey);

    boolean exists(String storageKey);
}
