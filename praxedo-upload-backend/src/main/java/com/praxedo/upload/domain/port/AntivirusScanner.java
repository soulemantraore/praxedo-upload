package com.praxedo.upload.domain.port;

import com.praxedo.upload.domain.models.ScanVerdict;

import java.time.Instant;

/**
 * Port : moteur antivirus. On lui donne la localisation logique du fichier ({@code storageKey}) ;
 * l'adapter decide comment y acceder (en gcp : appel HTTP a un scanner externe qui lit GCS ;
 * en local/test : lecture des octets via {@link FileStorage}).
 * Un echec technique (moteur indisponible, timeout) leve {@link ScanException} et n'est PAS un verdict.
 */
public interface AntivirusScanner {

    ScanVerdict scan(String storageKey, Instant scannedAt) throws ScanException;

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
