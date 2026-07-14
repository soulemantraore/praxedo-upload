package com.praxedo.upload.domain.models;

import java.util.UUID;

/** Criteres de recherche paginee des fichiers d'un owner (immuable). */
public record FileQuery(UUID ownerId, String q, FileStatus status, int page, int size) {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    public static FileQuery of(UUID ownerId, String q, FileStatus status, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return new FileQuery(ownerId, q, status, safePage, safeSize);
    }
}
