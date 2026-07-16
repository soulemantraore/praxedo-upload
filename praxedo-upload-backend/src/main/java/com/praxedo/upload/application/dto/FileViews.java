package com.praxedo.upload.application.dto;

import com.praxedo.upload.domain.models.FileRecord;
import com.praxedo.upload.domain.models.FileStatus;
import com.praxedo.upload.domain.models.ScanVerdict;
import com.praxedo.upload.domain.models.StatusCounts;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class FileViews {

    private FileViews() {
    }

    public record FileView(UUID id, String filename, String contentType, long sizeBytes,
                           FileStatus status, boolean infected, String threatName,
                           Instant createdAt, Instant scannedAt) {
        public static FileView of(FileRecord f) {
            ScanVerdict v = f.scanVerdict();
            return new FileView(f.id(), f.filename(), f.contentType(), f.sizeBytes(), f.status(),
                v != null && v.infected(), v == null ? null : v.threatName(), f.createdAt(), f.scannedAt());
        }
    }

    public record BatchView(UUID batchId, List<FileView> items, StatusCounts summary) {
    }

    /** URL signee de telechargement, renvoyee en JSON pour que le navigateur navigue directement
     *  vers le stockage (pas de proxy des octets, pas de preflight CORS ni de fuite de X-API-Key). */
    public record DownloadView(String url) {
    }
}
