package com.praxedo.upload.infrastructure.storage.local;

import com.praxedo.upload.domain.port.FileStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;

/**
 * Adapter local du port FileStorage (profils local/test). Les octets sont sur le filesystem ;
 * les "URLs signees" sont remplacees par des URLs proxy servies par l'app (voir LocalStorageController).
 */
@Component
@Profile({"local", "test"})
public class LocalFileStorage implements FileStorage {

    private final Path baseDir;
    private final String publicBaseUrl;

    public LocalFileStorage(
            @Value("${storage.local.base-dir}") String baseDir,
            @Value("${storage.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.baseDir = Path.of(baseDir);
        this.publicBaseUrl = publicBaseUrl;
    }

    private Path resolve(String key) {
        return baseDir.resolve(key).normalize();
    }

    public void write(String key, InputStream content) {
        try {
            Path p = resolve(key);
            Files.createDirectories(p.getParent());
            Files.copy(content, p, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public InputStream read(String key) {
        try {
            return Files.newInputStream(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(resolve(key));
    }

    @Override
    public UploadTarget createUploadTarget(String key, String contentType, long size) {
        return new UploadTarget(proxy("/api/_local/upload", key), Instant.now().plus(Duration.ofMinutes(15)));
    }

    @Override
    public URI createDownloadUrl(String key, Duration ttl) {
        return proxy("/api/_local/download", key);
    }

    private URI proxy(String path, String key) {
        String enc = URLEncoder.encode(key, StandardCharsets.UTF_8);
        return URI.create(publicBaseUrl + path + "?key=" + enc);
    }
}
