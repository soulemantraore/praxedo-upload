package com.praxedo.upload.domain.exceptions;

/** Taille declaree superieure au plafond autorise. Mappee en 413 par la couche web. */
public class FileTooLargeException extends RuntimeException {

    public FileTooLargeException(long sizeBytes, long maxBytes) {
        super("Fichier trop volumineux : " + sizeBytes + " octets (max " + maxBytes + " octets).");
    }
}
