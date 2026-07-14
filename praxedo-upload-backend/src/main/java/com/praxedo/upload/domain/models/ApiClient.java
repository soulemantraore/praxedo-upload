package com.praxedo.upload.domain.models;

import java.time.Instant;
import java.util.UUID;

/**
 * Un client d'API (systeme tiers / utilisateur). Identifie par sa cle API (stockee hachee).
 * L'id sert d'owner : chaque fichier est rattache a un ApiClient et scope par lui.
 */
public record ApiClient(UUID id, String name, String apiKeyHash, boolean active, Instant createdAt) {
}
