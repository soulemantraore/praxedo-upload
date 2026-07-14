package com.praxedo.upload.application;

import com.praxedo.upload.domain.models.ApiClient;
import com.praxedo.upload.infrastructure.persistence.inmemory.InMemoryApiClientRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyServiceTest {

    private final InMemoryApiClientRepository repo = new InMemoryApiClientRepository();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-10T10:00:00Z"), ZoneOffset.UTC);
    private final ApiKeyService service = new ApiKeyService(repo, () -> UUID.randomUUID(), clock);

    @Test
    void created_client_can_be_resolved_by_its_raw_key() {
        String rawKey = service.createClient("Batir SA");
        Optional<ApiClient> resolved = service.resolveOwner(rawKey);
        assertThat(resolved).isPresent();
        assertThat(resolved.get().name()).isEqualTo("Batir SA");
    }

    @Test
    void unknown_key_resolves_to_empty() {
        assertThat(service.resolveOwner("nope")).isEmpty();
    }

    @Test
    void raw_key_is_never_stored_in_clear() {
        String rawKey = service.createClient("X");
        assertThat(repo.findByApiKeyHash(rawKey)).isEmpty();
    }
}
