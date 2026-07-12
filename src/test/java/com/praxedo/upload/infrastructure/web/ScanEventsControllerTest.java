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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScanEventsControllerTest {

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
    void push_event_triggers_scan_and_marks_clean() throws Exception {
        String body = "{\"filename\":\"ok.txt\",\"contentType\":\"text/plain\",\"size\":5}";
        JsonNode created = json.readTree(mvc.perform(post("/api/files").header("X-API-Key", key)
                .contentType("application/json").content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String id = created.get("id").asText();
        String uploadUrl = created.get("uploadUrl").asText();
        String storageKey = URLDecoder.decode(
            uploadUrl.substring(uploadUrl.indexOf("key=") + 4), StandardCharsets.UTF_8);
        mvc.perform(put("/api/_local/upload").param("key", storageKey).content("hello"))
            .andExpect(status().isOk());

        // Message push Pub/Sub : data = base64(fileId)
        String data = Base64.getEncoder().encodeToString(id.getBytes(StandardCharsets.UTF_8));
        String pushBody = "{\"message\":{\"data\":\"" + data + "\",\"messageId\":\"m1\"},"
            + "\"subscription\":\"projects/p/subscriptions/s\"}";
        mvc.perform(post("/internal/scan-events").contentType("application/json").content(pushBody))
            .andExpect(status().isNoContent());

        mvc.perform(get("/api/files/" + id).header("X-API-Key", key))
            .andExpect(jsonPath("$.status").value("CLEAN"));
    }

    @Test
    void invalid_payload_is_acked_without_error() throws Exception {
        // base64("not-a-uuid")
        String pushBody = "{\"message\":{\"data\":\"bm90LWEtdXVpZA==\"}}";
        mvc.perform(post("/internal/scan-events").contentType("application/json").content(pushBody))
            .andExpect(status().isNoContent());
    }
}
