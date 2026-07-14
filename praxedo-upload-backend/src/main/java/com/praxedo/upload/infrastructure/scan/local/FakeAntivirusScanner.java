package com.praxedo.upload.infrastructure.scan.local;

import com.praxedo.upload.domain.file.ScanVerdict;
import com.praxedo.upload.domain.port.AntivirusScanner;
import com.praxedo.upload.domain.port.FileStorage;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Doublure locale du port AntivirusScanner (profils local/test) :
 * lit les octets via {@link FileStorage} et marque INFECTED tout contenu portant
 * la signature de test EICAR, sinon CLEAN.
 * (En gcp c'est {@link com.praxedo.upload.infrastructure.scan.gcp.RemoteScannerClient}.)
 */
@Component
@Profile({"local", "test"})
public class FakeAntivirusScanner implements AntivirusScanner {

    private static final String EICAR_MARKER = "EICAR-STANDARD-ANTIVIRUS-TEST-FILE";
    static final String ENGINE = "fake";

    private final FileStorage storage;

    public FakeAntivirusScanner(FileStorage storage) {
        this.storage = storage;
    }

    @Override
    public ScanVerdict scan(String storageKey, Instant scannedAt) throws ScanException {
        try (InputStream content = storage.read(storageKey)) {
            String body = new String(content.readAllBytes(), StandardCharsets.UTF_8);
            if (body.contains(EICAR_MARKER)) {
                return ScanVerdict.infected(ENGINE, "Eicar-Test-Signature", scannedAt);
            }
            return ScanVerdict.clean(ENGINE, scannedAt);
        } catch (IOException | UncheckedIOException e) {
            throw new ScanException("lecture du contenu impossible", e);
        }
    }
}
