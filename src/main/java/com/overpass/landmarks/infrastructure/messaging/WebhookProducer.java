package com.overpass.landmarks.infrastructure.messaging;

import com.overpass.landmarks.application.dto.WebhookEventDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Service for producing webhook events to Kafka.
 * Sends webhook requests to a Kafka topic for asynchronous processing.
 */
@Service
public class WebhookProducer {

    private static final Logger logger = LoggerFactory.getLogger(WebhookProducer.class);

    private final KafkaTemplate<String, WebhookEventDto> kafkaTemplate;
    private final String webhookTopic;

    public WebhookProducer(
            KafkaTemplate<String, WebhookEventDto> kafkaTemplate,
            @Value("${app.kafka.webhook-topic}") String webhookTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.webhookTopic = webhookTopic;
    }

    /**
     * Send a webhook event to Kafka topic for asynchronous processing.
     *
     * @param event Webhook event to send
     * @return CompletableFuture with send result
     */
    public CompletableFuture<SendResult<String, WebhookEventDto>> sendWebhookEvent(WebhookEventDto event) {
        logger.info("Sending webhook event to Kafka topic {}: eventId={}, lat={}, lng={}",
                webhookTopic, event.getId(), event.getLat(), event.getLng());

        CompletableFuture<SendResult<String, WebhookEventDto>> future = kafkaTemplate.send(
                webhookTopic,
                event.getId().toString(),
                event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                logger.info("Successfully sent webhook event to Kafka: eventId={}, offset={}",
                        event.getId(), result.getRecordMetadata().offset());
            } else {
                logger.error("Failed to send webhook event to Kafka: eventId={}", event.getId(), ex);
            }
        });

        return future;
    }
}
