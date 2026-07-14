package com.praxedo.upload.domain.models;

/** Compteurs de fichiers par statut pour un owner (cartes de metriques du frontend). */
public record StatusCounts(long total, long clean, long scanning, long pending, long blocked) {
}
