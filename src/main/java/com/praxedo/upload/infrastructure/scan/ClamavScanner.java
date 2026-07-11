package com.praxedo.upload.infrastructure.scan;

import com.praxedo.upload.domain.file.ScanVerdict;
import com.praxedo.upload.domain.port.AntivirusScanner;
import com.praxedo.upload.infrastructure.scan.clamav.ClamdClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;

/**
 * Adapter reel du port AntivirusScanner : delegue le scan a ClamAV (clamd) via le protocole INSTREAM.
 * Actif en profil gcp ; le domaine ne connait que le port, pas ClamAV.
 */
@Component
@Profile("gcp")
public class ClamavScanner implements AntivirusScanner {

    static final String ENGINE = "clamav";

    private final String host;
    private final int port;

    public ClamavScanner(@Value("${clamav.host:localhost}") String host,
                         @Value("${clamav.port:3310}") int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public ScanVerdict scan(InputStream content, Instant scannedAt) throws ScanException {
        try {
            ClamdClient.Response response = new ClamdClient(host, port).scan(content);
            return switch (response.status()) {
                case OK -> ScanVerdict.clean(ENGINE, scannedAt);
                case FOUND -> ScanVerdict.infected(ENGINE, response.detail(), scannedAt);
                case ERROR -> throw new ScanException("clamd a repondu ERROR: " + response.detail());
            };
        } catch (IOException e) {
            throw new ScanException("communication avec clamd impossible", e);
        }
    }
}
