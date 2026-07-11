package com.praxedo.upload.infrastructure.scan;

import com.google.api.core.ApiFutures;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.pubsub.v1.PubsubMessage;
import com.praxedo.upload.domain.port.ScanQueue;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Teste la logique de l'adapter (le message publie porte le bon fileId), en mockant la frontiere
 * Publisher. On ne teste pas le client Google (code tiers de confiance) mais NOTRE mapping.
 */
class PubSubScanQueueTest {

    @Test
    void enqueue_publishes_message_carrying_the_file_id() {
        Publisher publisher = mock(Publisher.class);
        when(publisher.publish(any(PubsubMessage.class))).thenReturn(ApiFutures.immediateFuture("msg-1"));
        PubSubScanQueue queue = new PubSubScanQueue(publisher);
        UUID fileId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        queue.enqueue(new ScanQueue.ScanRequest(fileId));

        ArgumentCaptor<PubsubMessage> captor = ArgumentCaptor.forClass(PubsubMessage.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().getData().toStringUtf8()).isEqualTo(fileId.toString());
        assertThat(captor.getValue().getAttributesMap()).containsEntry("fileId", fileId.toString());
    }
}
