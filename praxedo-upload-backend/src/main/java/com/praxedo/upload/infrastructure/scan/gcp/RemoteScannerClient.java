package com.praxedo.upload.infrastructure.scan.gcp;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import com.praxedo.upload.domain.file.ScanVerdict;
import com.praxedo.upload.domain.port.AntivirusScanner;
import com.praxedo.upload.infrastructure.config.ScannerProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.time.Instant;

/**
 * Adapter reel du port AntivirusScanner (profil gcp) : delegue le scan a un service HTTP externe
 * (le scanner Python). On lui envoie l'URI GCS de l'objet ({@code gs://bucket/key}) ; il lit GCS,
 * scanne via ClamAV et renvoie le verdict. Le scanner n'ecrit jamais en base : seul le worker le fait.
 *
 * <p>Appel authentifie par un jeton OIDC (le scanner est un service Cloud Run prive). Un echec
 * technique (service injoignable, non-2xx, corps illisible, "infected" sans nom de menace) leve
 * {@link ScanException} et n'est JAMAIS reporte comme un verdict CLEAN.
 */
@Component
@Profile("gcp")
public class RemoteScannerClient implements AntivirusScanner {

    static final String DEFAULT_ENGINE = "clamav";

    private final RestClient restClient;
    private final String bucket;
    private final ScannerProperties properties;

    /** Credentials OIDC construits paresseusement (pas d'ADC requis au demarrage / en test). */
    private volatile IdTokenCredentials idTokenCredentials;

    public RemoteScannerClient(RestClient scannerRestClient,
                               @Value("${storage.gcs.bucket}") String bucket,
                               ScannerProperties properties) {
        this.restClient = scannerRestClient;
        this.bucket = bucket;
        this.properties = properties;
    }

    @Override
    public ScanVerdict scan(String storageKey, Instant scannedAt) throws ScanException {
        String gsUri = "gs://" + bucket + "/" + storageKey;
        String authorization = properties.oidcEnabled() ? "Bearer " + idToken() : null;

        ScanResult result;
        try {
            RestClient.RequestBodySpec request = restClient.post()
                .uri("/scan")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON);
            if (authorization != null) {
                request = request.header("Authorization", authorization);
            }
            result = request.body(new ScanRequest(gsUri)).retrieve().body(ScanResult.class);
        } catch (RestClientException | HttpMessageConversionException e) {
            throw new ScanException("appel du scanner en echec: " + e.getMessage(), e);
        }

        if (result == null) {
            throw new ScanException("reponse du scanner vide");
        }
        String engine = (result.engine() != null && !result.engine().isBlank()) ? result.engine() : DEFAULT_ENGINE;
        if (result.infected()) {
            if (result.threatName() == null || result.threatName().isBlank()) {
                throw new ScanException("scanner: verdict infecte sans nom de menace");
            }
            return ScanVerdict.infected(engine, result.threatName(), scannedAt);
        }
        return ScanVerdict.clean(engine, scannedAt);
    }

    private String idToken() throws ScanException {
        try {
            IdTokenCredentials credentials = credentials();
            credentials.refreshIfExpired();
            return credentials.getIdToken().getTokenValue();
        } catch (IOException e) {
            throw new ScanException("obtention du jeton OIDC impossible", e);
        }
    }

    private IdTokenCredentials credentials() throws IOException {
        IdTokenCredentials local = idTokenCredentials;
        if (local == null) {
            synchronized (this) {
                local = idTokenCredentials;
                if (local == null) {
                    GoogleCredentials base = GoogleCredentials.getApplicationDefault();
                    if (!(base instanceof IdTokenProvider provider)) {
                        throw new IOException("les credentials par defaut ne fournissent pas d'ID token OIDC");
                    }
                    String audience = properties.audience() != null ? properties.audience() : properties.baseUrl();
                    local = IdTokenCredentials.newBuilder()
                        .setIdTokenProvider(provider)
                        .setTargetAudience(audience)
                        .build();
                    idTokenCredentials = local;
                }
            }
        }
        return local;
    }

    /** Corps JSON du contrat fige avec le scanner Python. */
    record ScanRequest(String gsUri) {}

    record ScanResult(boolean infected, String engine, String threatName) {}
}
