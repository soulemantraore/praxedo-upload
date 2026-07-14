package com.praxedo.upload.infrastructure.persistence.jpa.repositories;

import com.praxedo.upload.infrastructure.persistence.jpa.entities.ApiClientEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Repository Spring Data JPA des clients d'API. */
public interface ApiClientJpaRepository extends JpaRepository<ApiClientEntity, UUID> {

    Optional<ApiClientEntity> findByApiKeyHash(String apiKeyHash);
}
