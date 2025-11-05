package com.overpass.landmarks.infrastructure.config;

import com.overpass.landmarks.application.dto.WebhookEventDto;
import com.overpass.landmarks.application.service.WebhookService;
import com.overpass.landmarks.infrastructure.messaging.WebhookProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

/**
 * Test configuration for Kafka components.
 * Provides a mock WebhookProducer that processes webhooks synchronously for testing.
 */
@Configuration
@Profile("test")
public class TestKafkaConfig {

    private static final Logger logger = LoggerFactory.getLogger(TestKafkaConfig.class);

    /**
     * Mock WebhookProducer for tests that processes webhooks synchronously
     * instead of sending to Kafka.
     */
    @Bean
    @Primary
    public WebhookProducer webhookProducer(WebhookService webhookService,
            @Value("${app.kafka.webhook-topic:webhook-events}") String webhookTopic) {
        // Create a mock KafkaTemplate that won't be used
        KafkaTemplate<String, WebhookEventDto> mockKafkaTemplate = null;
        
        return new WebhookProducer(mockKafkaTemplate, webhookTopic) {
            @Override
            public CompletableFuture<SendResult<String, WebhookEventDto>> sendWebhookEvent(WebhookEventDto event) {
                logger.info("Test mode: Processing webhook synchronously instead of sending to Kafka: eventId={}, lat={}, lng={}",
                        event.getId(), event.getLat(), event.getLng());
                
                // Process synchronously for tests
                try {
                    webhookService.processWebhook(event.getLat(), event.getLng());
                    
                    // Return a completed future with a mock result
                    return CompletableFuture.completedFuture(null);
                } catch (Exception e) {
                    CompletableFuture<SendResult<String, WebhookEventDto>> future = new CompletableFuture<>();
                    future.completeExceptionally(e);
                    return future;
                }
            }
        };
    }
}

