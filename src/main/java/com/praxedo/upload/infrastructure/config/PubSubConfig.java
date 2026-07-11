package com.praxedo.upload.infrastructure.config;

import com.google.cloud.pubsub.v1.Publisher;
import com.google.pubsub.v1.TopicName;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.IOException;

/** Fournit le Publisher Pub/Sub (profil gcp), configure depuis {@link PubSubProperties}. */
@Configuration
@Profile("gcp")
public class PubSubConfig {

    @Bean(destroyMethod = "shutdown")
    public Publisher scanRequestPublisher(PubSubProperties properties) throws IOException {
        return Publisher.newBuilder(TopicName.of(properties.projectId(), properties.scanTopic())).build();
    }
}
