package com.praxedo.upload.domain.port;

import com.praxedo.upload.domain.file.ScanVerdict;

import java.io.InputStream;
import java.time.Instant;

/**
 * Port : moteur antivirus. Impl par defaut = ClamAV (protocole clamd), adapter SaaS trivial a brancher.
 * Un echec technique (moteur indisponible, timeout) leve {@link ScanException} et n'est PAS un verdict.
 */
public interface AntivirusScanner {

    ScanVerdict scan(InputStream content, Instant scannedAt) throws ScanException;

    /** Echec technique du scan (a distinguer d'un verdict INFECTED). */
    class ScanException extends Exception {
        public ScanException(String message, Throwable cause) {
            super(message, cause);
        }

        public ScanException(String message) {
            super(message);
        }
    }
}
