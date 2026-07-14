package com.praxedo.upload.infrastructure.scan;

import com.praxedo.upload.application.FileScanService;
import com.praxedo.upload.domain.port.ScanQueue;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** Profil local : scan asynchrone dans le meme processus (remplace par Pub/Sub au jalon 2). */
@Component
@Profile("local")
public class InProcessScanQueue implements ScanQueue {

    private final FileScanService scanService;

    public InProcessScanQueue(FileScanService scanService) {
        this.scanService = scanService;
    }

    @Async
    @Override
    public void enqueue(ScanRequest request) {
        scanService.scan(request.fileId());
    }
}
