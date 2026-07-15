package com.praxedo.upload.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test unitaire (sans contexte Spring) de la normalisation des origines CORS.
 * Une origine CORS ne doit jamais porter de slash final (l'entete Origin du navigateur
 * n'en a jamais) : on le retire, et on ignore les entrees vides.
 */
class SecurityConfigCorsSourceTest {

    @Test
    void strips_trailing_slash_and_ignores_blank_origins() {
        var source = (UrlBasedCorsConfigurationSource) new SecurityConfig()
            .corsConfigurationSource(List.of("https://ui.example.com/", "   ", "https://alt.example.com"));

        CorsConfiguration config = source.getCorsConfigurations().get("/api/**");
        assertThat(config.getAllowedOrigins())
            .containsExactly("https://ui.example.com", "https://alt.example.com");
    }
}
