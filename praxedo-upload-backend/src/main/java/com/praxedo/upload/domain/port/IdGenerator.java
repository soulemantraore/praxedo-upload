package com.praxedo.upload.domain.port;

import java.util.UUID;

/** Port : generation d'identifiants. Injecte pour rendre les tests deterministes. */
public interface IdGenerator {
    UUID newId();
}
