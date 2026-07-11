package com.praxedo.upload.infrastructure.web;

import com.praxedo.upload.application.ApiKeyService;
import com.praxedo.upload.domain.client.ApiClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyAuthFilterTest {

    private final ApiKeyService apiKeyService = Mockito.mock(ApiKeyService.class);
    private final ApiKeyAuthFilter filter = new ApiKeyAuthFilter(apiKeyService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void valid_key_sets_authentication() throws Exception {
        UUID ownerId = UUID.randomUUID();
        Mockito.when(apiKeyService.resolveOwner("good"))
            .thenReturn(Optional.of(new ApiClient(ownerId, "X", "h", true, Instant.now())));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-API-Key", "good");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(((AuthenticatedClient) auth.getPrincipal()).ownerId()).isEqualTo(ownerId);
    }

    @Test
    void missing_key_leaves_context_empty() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
