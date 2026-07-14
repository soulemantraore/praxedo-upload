package com.praxedo.upload.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Configuration du scanner antivirus distant (profil gcp).
 *
 * <ul>
 *   <li>{@code baseUrl}  : URL du service Cloud Run scanner.</li>
 *   <li>{@code audience} : audience du jeton OIDC service-a-service (souvent = baseUrl).</li>
 *   <li>{@code oidcEnabled} : joindre un jeton OIDC (true en prod ; false en local/emulateur).</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "scanner")
public record ScannerProperties(
    String baseUrl,
    String audience,
    @DefaultValue("true") boolean oidcEnabled,
    @DefaultValue("5s") Duration connectTimeout,
    @DefaultValue("550s") Duration readTimeout) {
}
