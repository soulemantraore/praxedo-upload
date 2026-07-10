package com.praxedo.upload.infrastructure.scan;

import com.praxedo.upload.domain.file.ScanVerdict;
import com.praxedo.upload.domain.port.AntivirusScanner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Doublure locale du port AntivirusScanner (profils local/test) :
 * marque INFECTED tout contenu portant la signature de test EICAR, sinon CLEAN.
 */
@Component
@Profile({"local", "test"})
public class FakeAntivirusScanner implements AntivirusScanner {

    private static final String EICAR_MARKER = "EICAR-STANDARD-ANTIVIRUS-TEST-FILE";
    static final String ENGINE = "fake";

    @Override
    public ScanVerdict scan(InputStream content, Instant scannedAt) throws ScanException {
        try {
            String body = new String(content.readAllBytes(), StandardCharsets.UTF_8);
            if (body.contains(EICAR_MARKER)) {
                return ScanVerdict.infected(ENGINE, "Eicar-Test-Signature", scannedAt);
            }
            return ScanVerdict.clean(ENGINE, scannedAt);
        } catch (IOException e) {
            throw new ScanException("lecture du contenu impossible", e);
        }
    }
}
