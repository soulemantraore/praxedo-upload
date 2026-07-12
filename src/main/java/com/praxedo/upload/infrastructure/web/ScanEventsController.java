package com.praxedo.upload.infrastructure.web;

import com.praxedo.upload.application.FileScanService;
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
import java.util.UUID;

/**
 * Endpoint push Pub/Sub : recoit une notification de scan (data = fileId encode en base64),
 * declenche le scan, puis ack (204). On ack meme si le fileId est invalide/inconnu, pour eviter
 * des redeliveries infinies par Pub/Sub.
 * Securite : /internal/** est ouvert ici (jalon 2) ; en prod, l'endpoint est protege par le jeton
 * OIDC de la push subscription Pub/Sub (verifie via IAM Cloud Run).
 */
@RestController
@RequestMapping("/internal/scan-events")
public class ScanEventsController {

    private static final Logger log = LoggerFactory.getLogger(ScanEventsController.class);

    private final FileScanService scanService;

    public ScanEventsController(FileScanService scanService) {
        this.scanService = scanService;
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody PubSubPushMessage push) {
        UUID fileId = extractFileId(push);
        if (fileId != null) {
            scanService.scan(fileId);
        }
        return ResponseEntity.noContent().build();
    }

    private UUID extractFileId(PubSubPushMessage push) {
        if (push == null || push.message() == null || push.message().data() == null) {
            log.warn("message push Pub/Sub sans data, ignore");
            return null;
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(push.message().data()), StandardCharsets.UTF_8);
            return UUID.fromString(decoded.trim());
        } catch (IllegalArgumentException e) {
            log.warn("payload push Pub/Sub invalide, ignore : {}", e.getMessage());
            return null;
        }
    }
}
