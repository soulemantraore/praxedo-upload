package com.praxedo.upload.domain.models;

import java.util.Map;
import java.util.Set;

/**
 * Statut d'un fichier dans son cycle de vie et transitions autorisees.
 * Invariant de securite : seul {@link #CLEAN} est telechargeable.
 */
public enum FileStatus {
    PENDING,
    SCANNING,
    CLEAN,
    INFECTED,
    SCAN_FAILED,
    EXPIRED;

    private static final Map<FileStatus, Set<FileStatus>> ALLOWED = Map.ofEntries(
        Map.entry(PENDING, Set.of(SCANNING, EXPIRED)),
        Map.entry(SCANNING, Set.of(CLEAN, INFECTED, SCAN_FAILED)),
        Map.entry(SCAN_FAILED, Set.of(SCANNING)),
        Map.entry(CLEAN, Set.of()),
        Map.entry(INFECTED, Set.of()),
        Map.entry(EXPIRED, Set.of())
    );

    public boolean canTransitionTo(FileStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isDownloadable() {
        return this == CLEAN;
    }
}
