package com.praxedo.upload.application;

import com.praxedo.upload.domain.models.ApiClient;
import com.praxedo.upload.domain.port.ApiClientRepository;
import com.praxedo.upload.domain.port.IdGenerator;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class ApiKeyService {

    private final ApiClientRepository repository;
    private final IdGenerator ids;
    private final Clock clock;

    public ApiKeyService(ApiClientRepository repository, IdGenerator ids, Clock clock) {
        this.repository = repository;
        this.ids = ids;
        this.clock = clock;
    }

    /** Genere une cle, stocke seulement son hash, renvoie la cle en clair UNE seule fois. */
    public String createClient(String name) {
        String rawKey = "pk_" + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        ApiClient client = new ApiClient(ids.newId(), name, hash(rawKey), true, clock.instant());
        repository.save(client);
        return rawKey;
    }

    public Optional<ApiClient> resolveOwner(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }
        return repository.findByApiKeyHash(hash(rawKey));
    }

    private String hash(String rawKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
