package com.praxedo.upload.infrastructure.web;

import com.praxedo.upload.application.FileScanService;
import com.praxedo.upload.domain.file.FileRecord;
import com.praxedo.upload.domain.port.FileMetadataRepository;
import com.praxedo.upload.infrastructure.web.dto.PubSubPushMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Endpoint push Pub/Sub : declenche un scan a partir de deux types de messages, puis ack (204).
 * On ack meme si rien n'est declenchable (fileId inconnu, autre evenement) pour eviter les
 * redeliveries infinies de Pub/Sub.
 * <ol>
 *   <li><b>Notification GCS "object finalize"</b> : attribut {@code objectId} = storageKey
 *       -> resolution du fichier -> scan. C'est l'auto-trigger apres upload direct sur GCS.</li>
 *   <li><b>Message applicatif</b> (rescan, PubSubScanQueue) : {@code data} = fileId en base64.</li>
 * </ol>
 * Securite : /internal/** est ouvert ici ; en prod, protege par le jeton OIDC de la push subscription
 * (verifie via IAM Cloud Run).
 */
@RestController
@RequestMapping("/internal/scan-events")
public class ScanEventsController {

    private static final Logger log = LoggerFactory.getLogger(ScanEventsController.class);
    // eventType envoye par GCS lorsqu'un upload d'objet est finalise (valeur imposee par GCS).
    private static final String OBJECT_FINALIZE = "OBJECT_FINALIZE";

    private final FileScanService scanService;
    private final FileMetadataRepository fileRepository;

    public ScanEventsController(FileScanService scanService, FileMetadataRepository fileRepository) {
        this.scanService = scanService;
        this.fileRepository = fileRepository;
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody PubSubPushMessage push) {
        UUID fileId = resolveFileId(push);
        if (fileId != null) {
            scanService.scan(fileId);
        }
        return ResponseEntity.noContent().build();
    }

    private UUID resolveFileId(PubSubPushMessage push) {
        if (push == null || push.message() == null) {
            return null;
        }
        PubSubPushMessage.Message message = push.message();

        // 1) Notification GCS : l'attribut objectId porte le storageKey.
        Map<String, String> attributes = message.attributes();
        if (attributes != null && attributes.get("objectId") != null) {
            String eventType = attributes.get("eventType");
            if (eventType != null && !OBJECT_FINALIZE.equals(eventType)) {
                log.debug("evenement GCS ignore : {}", eventType);
                return null;
            }
            String storageKey = attributes.get("objectId");
            return fileRepository.findByStorageKey(storageKey)
                .map(FileRecord::id)
                .orElseGet(() -> {
                    log.warn("notification GCS pour un objet inconnu : {}", storageKey);
                    return null;
                });
        }

        // 2) Message applicatif : data = fileId (base64).
        return decodeFileId(message.data());
    }

    private UUID decodeFileId(String data) {
        if (data == null) {
            log.warn("message push sans data ni objectId, ignore");
            return null;
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(data), StandardCharsets.UTF_8);
            return UUID.fromString(decoded.trim());
        } catch (IllegalArgumentException e) {
            log.warn("payload push invalide, ignore : {}", e.getMessage());
            return null;
        }
    }
}
