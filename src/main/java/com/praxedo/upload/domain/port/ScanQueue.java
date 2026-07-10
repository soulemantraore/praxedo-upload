package com.praxedo.upload.domain.port;

import java.util.UUID;

/**
 * Port : file de demandes de scan. Adapter par defaut = Pub/Sub (jalon ulterieur) ;
 * adapters in-memory (sync/async) pour dev et tests.
 */
public interface ScanQueue {

    record ScanRequest(UUID fileId) {}

    void enqueue(ScanRequest request);
}
