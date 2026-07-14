package com.praxedo.upload.infrastructure.web;

import java.util.UUID;

/** Principal d'authentification : le client d'API resolu a partir de la cle. ownerId sert au scoping. */
public record AuthenticatedClient(UUID ownerId, String name) {
}
