package com.praxedo.upload.infrastructure.scan.local;

import com.praxedo.upload.application.FileScanService;
import com.praxedo.upload.domain.port.ScanQueue;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Profil test : scan synchrone sur le thread appelant (flux deterministe). */
@Component
@Profile("test")
public class SynchronousScanQueue implements ScanQueue {

    private final FileScanService scanService;

    public SynchronousScanQueue(FileScanService scanService) {
        this.scanService = scanService;
    }

    @Override
    public void enqueue(ScanRequest request) {
        scanService.scan(request.fileId());
    }
}
