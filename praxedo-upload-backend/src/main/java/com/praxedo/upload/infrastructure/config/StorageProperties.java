package com.praxedo.upload.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "storage")
public record StorageProperties(Local local, Duration uploadUrlTtl, Duration downloadUrlTtl, String publicBaseUrl) {

    public record Local(String baseDir) {
    }
}
