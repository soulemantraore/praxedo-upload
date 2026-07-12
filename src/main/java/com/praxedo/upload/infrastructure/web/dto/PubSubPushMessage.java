package com.praxedo.upload.infrastructure.web.dto;

import java.util.Map;

/**
 * Payload d'un message push Pub/Sub. Les champs inconnus (publishTime, orderingKey...) sont ignores
 * par Jackson (FAIL_ON_UNKNOWN_PROPERTIES desactive par defaut dans Spring Boot).
 */
public record PubSubPushMessage(Message message, String subscription) {

    public record Message(String data, String messageId, Map<String, String> attributes) {
    }
}
