package com.praxedo.upload.domain.models;

import java.time.Instant;
import java.util.Objects;

/**
 * Verdict d'un scan antivirus (value object immuable).
 * Un echec technique du scan n'est PAS un verdict : il est represente ailleurs (statut SCAN_FAILED).
 */
public record ScanVerdict(boolean infected, String engine, String threatName, Instant scannedAt) {

    public ScanVerdict {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(scannedAt, "scannedAt");
    }

    public static ScanVerdict clean(String engine, Instant scannedAt) {
        return new ScanVerdict(false, engine, null, scannedAt);
    }

    public static ScanVerdict infected(String engine, String threatName, Instant scannedAt) {
        return new ScanVerdict(true, engine, Objects.requireNonNull(threatName, "threatName"), scannedAt);
    }
}
