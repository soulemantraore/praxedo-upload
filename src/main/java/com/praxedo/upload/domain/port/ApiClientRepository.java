package com.praxedo.upload.domain.port;

import com.praxedo.upload.domain.client.ApiClient;

import java.util.Optional;

/** Port : persistance des clients d'API (cles hachees). Adapter par defaut = JPA/Postgres. */
public interface ApiClientRepository {

    /** Resout un client actif a partir du hash de sa cle API. */
    Optional<ApiClient> findByApiKeyHash(String apiKeyHash);

    void save(ApiClient client);
}
