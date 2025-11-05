package com.overpass.landmarks;

import com.overpass.landmarks.application.dto.WebhookEventDto;
import com.overpass.landmarks.application.dto.WebhookRequestDto;
import com.overpass.landmarks.domain.repository.CoordinateRequestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test that verifies Kafka is properly used for webhook processing.
 * Uses Testcontainers to spin up real Kafka and PostgreSQL instances.
 * 
 * Note: Does NOT use "test" profile to ensure Kafka components are enabled.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class WebhookKafkaIntegrationTest {

        @Container
        static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14-alpine")
                        .withDatabaseName("overpass")
                        .withUsername("postgres")
                        .withPassword("postgres");

        @Container
        static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private CoordinateRequestRepository coordinateRequestRepository;

        private static final String WEBHOOK_SECRET = "supersecret";
        private static final String WEBHOOK_TOPIC = "webhook-events";

        @DynamicPropertySource
        static void configureProperties(DynamicPropertyRegistry registry) {
                registry.add("spring.datasource.url", postgres::getJdbcUrl);
                registry.add("spring.datasource.username", postgres::getUsername);
                registry.add("spring.datasource.password", postgres::getPassword);
                registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
                registry.add("app.kafka.bootstrap-servers", kafka::getBootstrapServers);
                registry.add("app.webhook.secret", () -> WEBHOOK_SECRET);
                registry.add("app.kafka.webhook-topic", () -> WEBHOOK_TOPIC);
        }

        @BeforeEach
        void setUp() {
                coordinateRequestRepository.deleteAll();
        }

        @Test
        void testWebhook_SendsMessageToKafka() throws Exception {
                // Wait a bit for Kafka to be fully ready
                Thread.sleep(2000);

                // Create a Kafka consumer to verify messages are sent
                Properties consumerProps = new Properties();
                consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
                consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-group-" + System.currentTimeMillis());
                consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
                consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
                consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class.getName());
                consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
                consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, WebhookEventDto.class.getName());
                consumerProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

                Consumer<String, WebhookEventDto> consumer = new KafkaConsumer<>(consumerProps);
                consumer.subscribe(Collections.singletonList(WEBHOOK_TOPIC));

                // Send webhook request
                WebhookRequestDto request = new WebhookRequestDto(
                                new BigDecimal("48.8584"),
                                new BigDecimal("2.2945"));

                mockMvc.perform(post("/webhook")
                                .header("Authorization", "Bearer " + WEBHOOK_SECRET)
                                .contentType(APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isAccepted())
                                .andExpect(jsonPath("$.eventId").exists())
                                .andExpect(jsonPath("$.status").value("ACCEPTED"));

                // Wait for message to be sent to Kafka (give it time)
                Thread.sleep(1000);
                ConsumerRecords<String, WebhookEventDto> records = consumer.poll(Duration.ofSeconds(10));

                // Verify message was sent to Kafka
                assertThat(records.isEmpty()).isFalse();
                assertThat(records.count()).isGreaterThanOrEqualTo(1);

                consumer.close();
        }

        @Test
        void testWebhook_KafkaAsyncProcessing() throws Exception {
                WebhookRequestDto request = new WebhookRequestDto(
                                new BigDecimal("51.5074"), // London coordinates
                                new BigDecimal("-0.1278"));

                // Send webhook request
                String response = mockMvc.perform(post("/webhook")
                                .header("Authorization", "Bearer " + WEBHOOK_SECRET)
                                .contentType(APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isAccepted())
                                .andExpect(jsonPath("$.eventId").exists())
                                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                // Verify immediate response (Kafka async processing)
                assertThat(response).contains("\"eventId\"");

                // Initially, database should be empty or have minimal entries (async processing
                // hasn't completed)
                long initialCount = coordinateRequestRepository.count();

                // Wait for Kafka consumer to process the message (allow time for async
                // processing)
                Thread.sleep(8000);

                // After processing, verify data was persisted
                long finalCount = coordinateRequestRepository.count();
                assertThat(finalCount).isGreaterThan(initialCount);
        }

        @Test
        void testWebhook_MultipleMessages_AllProcessedViaKafka() throws Exception {
                // Send multiple webhook requests with different coordinates
                BigDecimal[] lats = {
                                new BigDecimal("40.7128"), // New York
                                new BigDecimal("34.0522"), // Los Angeles
                                new BigDecimal("41.8781") // Chicago
                };
                BigDecimal[] lngs = {
                                new BigDecimal("-74.0060"), // New York
                                new BigDecimal("-118.2437"), // Los Angeles
                                new BigDecimal("-87.6298") // Chicago
                };

                for (int i = 0; i < 3; i++) {
                        WebhookRequestDto request = new WebhookRequestDto(lats[i], lngs[i]);

                        mockMvc.perform(post("/webhook")
                                        .header("Authorization", "Bearer " + WEBHOOK_SECRET)
                                        .contentType(APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isAccepted())
                                        .andExpect(jsonPath("$.eventId").exists());
                }

                // Wait for all messages to be processed via Kafka
                Thread.sleep(8000);

                // Verify all requests were processed
                long count = coordinateRequestRepository.count();
                assertThat(count).isGreaterThanOrEqualTo(3);
        }
}
