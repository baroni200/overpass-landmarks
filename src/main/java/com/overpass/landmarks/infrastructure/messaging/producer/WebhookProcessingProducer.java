package com.overpass.landmarks.infrastructure.messaging.producer;

import com.overpass.landmarks.infrastructure.messaging.dto.WebhookProcessingMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka producer for sending webhook processing messages.
 * Sends messages to Kafka topic for asynchronous processing.
 */
@Service
public class WebhookProcessingProducer {

    private static final Logger logger = LoggerFactory.getLogger(WebhookProcessingProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topic.webhook-processing:webhook-processing}")
    private String webhookProcessingTopic;

    public WebhookProcessingProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Send webhook processing message to Kafka topic.
     * 
     * @param requestId The request ID
     * @param lat       Latitude
     * @param lng       Longitude
     */
    public void sendWebhookProcessingMessage(UUID requestId, BigDecimal lat, BigDecimal lng) {
        WebhookProcessingMessage message = new WebhookProcessingMessage(requestId, lat, lng);

        try {
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                    webhookProcessingTopic,
                    requestId.toString(),
                    message);

            // Wait for send to complete to ensure message is delivered
            // This is important for tests and ensures message is sent before continuing
            SendResult<String, Object> result = future.get();
            logger.info("Sent webhook processing message for request ID: {} to topic: {}, offset: {}",
                    requestId, webhookProcessingTopic, result.getRecordMetadata().offset());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while sending webhook processing message for request ID: {}",
                    requestId, e);
            throw new RuntimeException("Failed to send message to Kafka", e);
        } catch (Exception e) {
            logger.error("Error sending webhook processing message to Kafka for request ID: {}",
                    requestId, e);
            // Don't fail the request - Kafka might be temporarily unavailable
            // The message will be retried or handled by monitoring
            throw new RuntimeException("Failed to send message to Kafka", e);
        }
    }
}
