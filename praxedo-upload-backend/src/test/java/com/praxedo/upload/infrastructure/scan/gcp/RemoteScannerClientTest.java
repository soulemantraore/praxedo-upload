package com.praxedo.upload.infrastructure.scan.gcp;

import com.praxedo.upload.domain.file.ScanVerdict;
import com.praxedo.upload.domain.port.AntivirusScanner;
import com.praxedo.upload.infrastructure.config.ScannerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Teste le mapping HTTP <-> verdict et la transformation de tout echec technique en ScanException
 * (jamais un faux CLEAN). Le scanner distant est simule par MockRestServiceServer ; OIDC desactive.
 */
class RemoteScannerClientTest {

    private static final Instant NOW = Instant.parse("2026-07-10T10:00:00Z");

    private RestClient restClient;
    private MockRestServiceServer server;
    private RemoteScannerClient scanner;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://scanner");
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        ScannerProperties props = new ScannerProperties(
            "http://scanner", null, false, Duration.ofSeconds(5), Duration.ofSeconds(550));
        scanner = new RemoteScannerClient(restClient, "my-bucket", props);
    }

    @Test
    void clean_response_maps_to_clean_verdict() throws Exception {
        server.expect(requestTo("http://scanner/scan"))
            .andExpect(method(POST))
            .andExpect(jsonPath("$.gsUri").value("gs://my-bucket/owner/id/file.txt"))
            .andRespond(withSuccess("{\"infected\":false,\"engine\":\"clamav\",\"threatName\":null}",
                APPLICATION_JSON));

        ScanVerdict verdict = scanner.scan("owner/id/file.txt", NOW);

        assertThat(verdict.infected()).isFalse();
        assertThat(verdict.engine()).isEqualTo("clamav");
        server.verify();
    }

    @Test
    void infected_response_maps_to_infected_verdict_with_threat() throws Exception {
        server.expect(requestTo("http://scanner/scan"))
            .andRespond(withSuccess(
                "{\"infected\":true,\"engine\":\"clamav\",\"threatName\":\"Eicar-Test-Signature\"}",
                APPLICATION_JSON));

        ScanVerdict verdict = scanner.scan("owner/id/virus.txt", NOW);

        assertThat(verdict.infected()).isTrue();
        assertThat(verdict.threatName()).isEqualTo("Eicar-Test-Signature");
        server.verify();
    }

    @Test
    void server_error_becomes_scan_exception_not_clean() {
        server.expect(requestTo("http://scanner/scan")).andRespond(withServerError());

        assertThatThrownBy(() -> scanner.scan("owner/id/file.txt", NOW))
            .isInstanceOf(AntivirusScanner.ScanException.class);
    }

    @Test
    void malformed_body_becomes_scan_exception() {
        server.expect(requestTo("http://scanner/scan"))
            .andRespond(withSuccess("not-json-at-all", APPLICATION_JSON));

        assertThatThrownBy(() -> scanner.scan("owner/id/file.txt", NOW))
            .isInstanceOf(AntivirusScanner.ScanException.class);
    }

    @Test
    void infected_without_threat_name_becomes_scan_exception() {
        server.expect(requestTo("http://scanner/scan"))
            .andRespond(withSuccess("{\"infected\":true,\"engine\":\"clamav\",\"threatName\":null}",
                APPLICATION_JSON));

        assertThatThrownBy(() -> scanner.scan("owner/id/file.txt", NOW))
            .isInstanceOf(AntivirusScanner.ScanException.class);
    }
}
