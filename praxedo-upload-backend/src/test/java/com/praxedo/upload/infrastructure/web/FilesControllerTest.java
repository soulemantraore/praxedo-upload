package com.praxedo.upload.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.praxedo.upload.application.ApiKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FilesControllerTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ApiKeyService apiKeyService;
    @Autowired
    ObjectMapper json;

    String key;

    @BeforeEach
    void setup() {
        key = apiKeyService.createClient("Test SA");
    }

    @Test
    void unauthenticated_request_is_401() throws Exception {
        mvc.perform(get("/api/files")).andExpect(status().isUnauthorized());
    }

    @Test
    void register_upload_returns_201_with_upload_url() throws Exception {
        String body = "{\"filename\":\"a.pdf\",\"contentType\":\"application/pdf\",\"size\":100}";
        mvc.perform(post("/api/files").header("X-API-Key", key)
                .contentType("application/json").content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.uploadUrl").exists());
    }

    @Test
    void full_flow_clean_file_is_downloadable() throws Exception {
        String body = "{\"filename\":\"ok.txt\",\"contentType\":\"text/plain\",\"size\":5}";
        String res = mvc.perform(post("/api/files").header("X-API-Key", key)
                .contentType("application/json").content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(res);
        String id = node.get("id").asText();
        String uploadUrl = node.get("uploadUrl").asText();
        // Cle decodee (etat que le serveur recoit d'un vrai client) passee via .param pour eviter
        // le double-encodage du template MockMvc sur les %2F de la cle.
        String storageKey = java.net.URLDecoder.decode(
            uploadUrl.substring(uploadUrl.indexOf("key=") + 4), java.nio.charset.StandardCharsets.UTF_8);

        mvc.perform(put("/api/_local/upload").param("key", storageKey).content("hello"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/files/" + id + "/rescan").header("X-API-Key", key))
            .andExpect(status().isAccepted());
        mvc.perform(get("/api/files/" + id).header("X-API-Key", key))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLEAN"));
        mvc.perform(get("/api/files/" + id + "/content").header("X-API-Key", key))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.url").exists());
    }

    @Test
    void register_upload_over_max_size_returns_413() throws Exception {
        String body = "{\"filename\":\"big.bin\",\"contentType\":\"application/octet-stream\",\"size\":2000000000}";
        mvc.perform(post("/api/files").header("X-API-Key", key)
                .contentType("application/json").content(body))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.error").exists());
    }
}
