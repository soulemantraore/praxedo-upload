package com.praxedo.upload.domain.file.exceptions;

import java.util.UUID;

/** Fichier (ou lot) introuvable pour l'owner courant. Mappe en 404 par la couche web. */
public class FileNotFoundException extends RuntimeException {

    public FileNotFoundException(UUID id) {
        super("Fichier introuvable : " + id);
    }
}
