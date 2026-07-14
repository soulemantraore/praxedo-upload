package com.praxedo.upload.infrastructure.persistence.inmemory;

import com.praxedo.upload.domain.client.ApiClient;
import com.praxedo.upload.domain.port.ApiClientRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Profile({"local", "test"})
public class InMemoryApiClientRepository implements ApiClientRepository {

    private final Map<String, ApiClient> byHash = new ConcurrentHashMap<>();

    @Override
    public Optional<ApiClient> findByApiKeyHash(String apiKeyHash) {
        return Optional.ofNullable(byHash.get(apiKeyHash)).filter(ApiClient::active);
    }

    @Override
    public void save(ApiClient client) {
        byHash.put(client.apiKeyHash(), client);
    }
}
