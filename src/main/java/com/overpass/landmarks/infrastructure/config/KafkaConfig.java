package com.overpass.landmarks.infrastructure.config;

import com.overpass.landmarks.application.dto.WebhookEventDto;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for webhook event processing.
 * Configures KafkaTemplate for producing messages and listener container
 * factory for consuming.
 * 
 * Excluded from test profile to avoid requiring Kafka during tests.
 */
@Configuration
@Profile("!test")
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${app.kafka.consumer-group}")
    private String consumerGroup;

    /**
     * Producer factory for creating KafkaTemplate.
     */
    @Bean
    public ProducerFactory<String, WebhookEventDto> webhookProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * KafkaTemplate for sending webhook events to Kafka.
     */
    @Bean
    public KafkaTemplate<String, WebhookEventDto> webhookKafkaTemplate() {
        return new KafkaTemplate<>(webhookProducerFactory());
    }

    /**
     * Consumer factory for creating Kafka listener containers.
     */
    @Bean
    public ConsumerFactory<String, WebhookEventDto> webhookConsumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroup);
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Configure JsonDeserializer
        JsonDeserializer<WebhookEventDto> deserializer = new JsonDeserializer<>(WebhookEventDto.class, false);
        deserializer.addTrustedPackages("*");
        deserializer.setUseTypeHeaders(false);

        return new DefaultKafkaConsumerFactory<>(configProps, new StringDeserializer(), deserializer);
    }

    /**
     * Listener container factory for processing webhook events.
     * Configured with manual acknowledgment to ensure messages are only
     * acknowledged after successful processing.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, WebhookEventDto> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, WebhookEventDto> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(webhookConsumerFactory());
        factory.setConcurrency(3); // Process up to 3 messages concurrently
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}
