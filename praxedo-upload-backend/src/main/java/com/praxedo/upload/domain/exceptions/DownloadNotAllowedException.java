package com.praxedo.upload.domain.exceptions;

import com.praxedo.upload.domain.models.FileStatus;

/** Tentative de telechargement d'un fichier non CLEAN. Mappe en 403 par la couche web. */
public class DownloadNotAllowedException extends RuntimeException {

    public DownloadNotAllowedException(FileStatus status) {
        super("Telechargement refuse : statut " + status + " (seul CLEAN est telechargeable)");
    }
}
