package com.praxedo.upload.infrastructure.web.controllers;

import com.praxedo.upload.infrastructure.storage.local.LocalFileStorage;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proxy local qui simule GCS (profils local/test) : le client PUT/GET les octets ici au lieu
 * d'une URL signee. En prod (profil gcp) ce controleur n'existe pas.
 */
@RestController
@RequestMapping("/api/_local")
@Profile({"local", "test"})
public class LocalStorageController {

    private final LocalFileStorage storage;

    public LocalStorageController(LocalFileStorage storage) {
        this.storage = storage;
    }

    @PutMapping("/upload")
    public ResponseEntity<Void> upload(@RequestParam String key, HttpServletRequest request) throws Exception {
        storage.write(key, request.getInputStream());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> download(@RequestParam String key) {
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(new InputStreamResource(storage.read(key)));
    }
}
