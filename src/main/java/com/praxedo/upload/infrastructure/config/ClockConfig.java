package com.praxedo.upload.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Fournit un {@link Clock} injectable partout dans le domaine et l'application.
 * Injecter Clock (plutot que d'appeler Instant.now()) rend le temps deterministe en test.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
