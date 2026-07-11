package com.praxedo.upload.infrastructure.scan;

import com.google.api.core.ApiFuture;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.praxedo.upload.domain.port.ScanQueue;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

/**
 * Adapter reel du port ScanQueue : publie les demandes de scan sur un topic Pub/Sub.
 * Actif en profil gcp ; en local/test ce sont les queues in-process/synchrone qui sont utilisees.
 */
@Component
@Profile("gcp")
public class PubSubScanQueue implements ScanQueue {

    private final Publisher publisher;

    public PubSubScanQueue(Publisher scanRequestPublisher) {
        this.publisher = scanRequestPublisher;
    }

    @Override
    public void enqueue(ScanRequest request) {
        String fileId = request.fileId().toString();
        PubsubMessage message = PubsubMessage.newBuilder()
            .setData(ByteString.copyFromUtf8(fileId))
            .putAttributes("fileId", fileId)
            .build();
        try {
            ApiFuture<String> published = publisher.publish(message);
            published.get(); // garantit que le message est bien parti avant de rendre la main
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("publication Pub/Sub interrompue", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("publication Pub/Sub echouee", e);
        }
    }
}
