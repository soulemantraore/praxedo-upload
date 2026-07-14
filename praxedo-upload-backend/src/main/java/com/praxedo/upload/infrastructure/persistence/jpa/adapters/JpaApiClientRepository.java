package com.praxedo.upload.infrastructure.persistence.jpa.adapters;

import com.praxedo.upload.domain.client.ApiClient;
import com.praxedo.upload.domain.port.ApiClientRepository;
import com.praxedo.upload.infrastructure.persistence.jpa.entities.ApiClientEntity;
import com.praxedo.upload.infrastructure.persistence.jpa.repositories.ApiClientJpaRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Adapter par defaut du port ApiClientRepository : JPA / Postgres (Supabase, profil gcp). */
@Repository
@Profile("gcp")
public class JpaApiClientRepository implements ApiClientRepository {

    private final ApiClientJpaRepository jpa;

    public JpaApiClientRepository(ApiClientJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<ApiClient> findByApiKeyHash(String apiKeyHash) {
        return jpa.findByApiKeyHash(apiKeyHash).map(ApiClientEntity::toDomain).filter(ApiClient::active);
    }

    @Override
    public void save(ApiClient client) {
        jpa.save(ApiClientEntity.fromDomain(client));
    }
}
