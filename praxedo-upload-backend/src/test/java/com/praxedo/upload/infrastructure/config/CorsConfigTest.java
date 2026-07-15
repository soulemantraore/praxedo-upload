package com.praxedo.upload.infrastructure.config;

import com.praxedo.upload.application.ApiKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifie que le backend renvoie les entetes CORS attendus pour l'origine de l'UI.
 * Sans cela, le navigateur bloque tous les fetches UI -> API (preflight OPTIONS rejete).
 * L'origine testee doit correspondre a app.cors.allowed-origins de application-test.yml.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsConfigTest {

    private static final String UI_ORIGIN = "https://praxedo-ui.example.run.app";

    @Autowired
    MockMvc mvc;
    @Autowired
    ApiKeyService apiKeyService;

    @Test
    void preflight_from_allowed_origin_is_permitted_with_cors_headers() throws Exception {
        mvc.perform(options("/api/files")
                .header("Origin", UI_ORIGIN)
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "X-API-Key"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", UI_ORIGIN))
            .andExpect(header().string("Access-Control-Allow-Headers", containsString("X-API-Key")));
    }

    @Test
    void authenticated_request_from_allowed_origin_carries_cors_header() throws Exception {
        String key = apiKeyService.createClient("Test SA");
        mvc.perform(get("/api/files").header("X-API-Key", key).header("Origin", UI_ORIGIN))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", UI_ORIGIN));
    }

    @Test
    void preflight_from_unknown_origin_gets_no_cors_header() throws Exception {
        mvc.perform(options("/api/files")
                .header("Origin", "https://evil.example.com")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
