package com.praxedo.upload.infrastructure.persistence.jpa.entities;

import com.praxedo.upload.domain.client.ApiClient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Modele de persistance JPA du client d'API (distinct du domaine ApiClient). */
@Entity
@Table(name = "api_client")
public class ApiClientEntity {

    @Id
    private UUID id;
    @Column(nullable = false)
    private String name;
    @Column(name = "api_key_hash", nullable = false, unique = true)
    private String apiKeyHash;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ApiClientEntity() {
    }

    public static ApiClientEntity fromDomain(ApiClient c) {
        ApiClientEntity e = new ApiClientEntity();
        e.id = c.id();
        e.name = c.name();
        e.apiKeyHash = c.apiKeyHash();
        e.active = c.active();
        e.createdAt = c.createdAt();
        return e;
    }

    public ApiClient toDomain() {
        return new ApiClient(id, name, apiKeyHash, active, createdAt);
    }
}
