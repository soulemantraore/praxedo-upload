package com.praxedo.upload.infrastructure.config;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Fournit le client Storage GCS (profil gcp), via les credentials/projet par defaut de l'environnement. */
@Configuration
@Profile("gcp")
public class GcsConfig {

    @Bean
    public Storage storage() {
        return StorageOptions.getDefaultInstance().getService();
    }
}
