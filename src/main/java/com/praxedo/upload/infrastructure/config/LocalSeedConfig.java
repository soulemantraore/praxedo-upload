package com.praxedo.upload.infrastructure.config;

import com.praxedo.upload.application.ApiKeyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Profil local : cree un client de demo au demarrage et logue sa cle API en clair (dev uniquement). */
@Configuration
@Profile("local")
public class LocalSeedConfig {

    private static final Logger log = LoggerFactory.getLogger(LocalSeedConfig.class);

    @Bean
    public CommandLineRunner seedApiClient(ApiKeyService apiKeyService) {
        return args -> {
            String key = apiKeyService.createClient("Batir SA (demo locale)");
            log.info("Cle API de demo (profil local) : {}", key);
        };
    }
}
