package com.praxedo.upload.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration Pub/Sub (profil gcp) : projet GCP et topic des demandes de scan. */
@ConfigurationProperties(prefix = "pubsub")
public record PubSubProperties(String projectId, String scanTopic) {
}
