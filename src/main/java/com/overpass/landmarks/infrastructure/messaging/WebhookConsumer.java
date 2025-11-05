package com.overpass.landmarks.infrastructure.messaging;

import com.overpass.landmarks.application.dto.WebhookEventDto;
import com.overpass.landmarks.application.service.WebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for processing webhook events asynchronously.
 * Listens to webhook events topic and processes them using WebhookService.
 * 
 * Excluded from test profile to avoid requiring Kafka during tests.
 */
@Component
@Profile("!test")
public class WebhookConsumer {

    private static final Logger logger = LoggerFactory.getLogger(WebhookConsumer.class);

    private final WebhookService webhookService;

    public WebhookConsumer(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    /**
     * Process webhook events from Kafka topic.
     * This method is called automatically by Spring Kafka when a message arrives.
     *
     * @param event          Webhook event to process
     * @param acknowledgment Kafka acknowledgment for manual commit
     * @param partition      Kafka partition number
     * @param offset         Kafka offset
     */
    @KafkaListener(topics = "${app.kafka.webhook-topic}", groupId = "${app.kafka.consumer-group}", containerFactory = "kafkaListenerContainerFactory")
    public void consumeWebhookEvent(
            @Payload WebhookEventDto event,
            Acknowledgment acknowledgment,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        logger.info("Received webhook event from Kafka: eventId={}, lat={}, lng={}, partition={}, offset={}",
                event.getId(), event.getLat(), event.getLng(), partition, offset);

        try {
            // Process the webhook asynchronously
            webhookService.processWebhook(event.getLat(), event.getLng());

            logger.info("Successfully processed webhook event: eventId={}", event.getId());

            // Acknowledge the message after successful processing
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
        } catch (Exception e) {
            logger.error("Failed to process webhook event: eventId={}", event.getId(), e);
            // In a production system, you might want to implement retry logic or dead
            // letter queue
            // For now, we'll acknowledge anyway to prevent infinite retries
            // In production, consider using a Dead Letter Queue or retry mechanism
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
            throw e; // Re-throw to allow Kafka to handle retries if configured
        }
    }
}
