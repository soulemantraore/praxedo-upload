package com.praxedo.upload.infrastructure.web.helpers;

import com.praxedo.upload.domain.exceptions.DownloadNotAllowedException;
import com.praxedo.upload.domain.exceptions.FileNotFoundException;
import com.praxedo.upload.domain.exceptions.IllegalFileTransitionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Traduit les exceptions du domaine en reponses HTTP (le domaine ne connait pas HTTP). */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FileNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(FileNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(DownloadNotAllowedException.class)
    public ResponseEntity<Map<String, String>> forbidden(DownloadNotAllowedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalFileTransitionException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalFileTransitionException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }
}
