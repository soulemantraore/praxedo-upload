package com.praxedo.upload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.praxedo.upload.application.ApiKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tests de bout en bout du flux : garantie de securite (infecte bloque) + isolation entre owners. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EndToEndFlowTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ApiKeyService apiKeyService;
    @Autowired
    ObjectMapper json;

    // Signature de test standard EICAR (inoffensive).
    private static final String EICAR =
        "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

    private JsonNode register(String key, String filename, int size) throws Exception {
        String body = "{\"filename\":\"" + filename + "\",\"contentType\":\"text/plain\",\"size\":" + size + "}";
        String res = mvc.perform(post("/api/files").header("X-API-Key", key)
                .contentType("application/json").content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(res);
    }

    private void uploadBytes(String uploadUrl, String content) throws Exception {
        String storageKey = URLDecoder.decode(
            uploadUrl.substring(uploadUrl.indexOf("key=") + 4), StandardCharsets.UTF_8);
        mvc.perform(put("/api/_local/upload").param("key", storageKey).content(content))
            .andExpect(status().isOk());
    }

    @Test
    void infected_file_is_blocked_from_download() throws Exception {
        String key = apiKeyService.createClient("Owner A");
        JsonNode created = register(key, "virus.txt", EICAR.length());
        String id = created.get("id").asText();

        uploadBytes(created.get("uploadUrl").asText(), EICAR);
        mvc.perform(post("/api/files/" + id + "/rescan").header("X-API-Key", key))
            .andExpect(status().isAccepted());

        mvc.perform(get("/api/files/" + id).header("X-API-Key", key))
            .andExpect(jsonPath("$.status").value("INFECTED"));
        mvc.perform(get("/api/files/" + id + "/content").header("X-API-Key", key))
            .andExpect(status().isForbidden());
    }

    @Test
    void owner_isolation_is_enforced() throws Exception {
        String keyA = apiKeyService.createClient("Owner A");
        String keyB = apiKeyService.createClient("Owner B");
        String id = register(keyA, "a.txt", 5).get("id").asText();

        mvc.perform(get("/api/files/" + id).header("X-API-Key", keyB))
            .andExpect(status().isNotFound());
    }
}
