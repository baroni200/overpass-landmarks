package com.overpass.landmarks;

import com.overpass.landmarks.api.dto.LandmarksQueryResponseDto;
import com.overpass.landmarks.api.dto.WebhookRequestDto;
import com.overpass.landmarks.api.dto.WebhookSubmissionResponseDto;
import com.overpass.landmarks.application.port.out.CoordinateRequestRepository;
import com.overpass.landmarks.domain.model.CoordinateRequest;
import com.overpass.landmarks.infrastructure.cache.TestCacheConfig;
import com.overpass.landmarks.infrastructure.persistence.CoordinateRequestJpaRepository;
import com.overpass.landmarks.module.test.support.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static com.overpass.landmarks.module.test.support.TestFixtures.Coordinates;
import static com.overpass.landmarks.module.test.support.TestFixtures.Common;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestCacheConfig.class)
@Transactional
class WebhookIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private CoordinateRequestRepository coordinateRequestRepository;

        @Autowired
        private CoordinateRequestJpaRepository coordinateRequestJpaRepository;

        @Autowired
        private org.springframework.cache.CacheManager cacheManager;

        /**
         * Clear caches before each test to ensure test isolation.
         */
        @BeforeEach
        void clearCaches() {
                org.springframework.cache.Cache landmarksCache = cacheManager.getCache("landmarks");
                if (landmarksCache != null) {
                        landmarksCache.clear();
                }
                org.springframework.cache.Cache coordinateRequestsCache = cacheManager.getCache("coordinateRequests");
                if (coordinateRequestsCache != null) {
                        coordinateRequestsCache.clear();
                }
        }

        /**
         * Helper method to wait for async processing to complete.
         * Polls the GET endpoint until processing is done or timeout.
         */
        private void waitForProcessing(UUID requestId, long timeoutSeconds) throws Exception {
                long startTime = System.currentTimeMillis();
                while (System.currentTimeMillis() - startTime < timeoutSeconds * 1000) {
                        int status = mockMvc.perform(get("/webhook/" + requestId))
                                        .andReturn()
                                        .getResponse()
                                        .getStatus();
                        if (status == 200) {
                                return; // Processing complete
                        }
                        Thread.sleep(100); // Wait 100ms before next poll
                }
        }

        @Test
        void testWebhook_ValidRequest_Returns202() throws Exception {
                // Use coordinates that don't exist in seed data to ensure new request is
                // created
                WebhookRequestDto request = TestFixtures.webhookRequest(
                                Coordinates.NEW_YORK_LAT,
                                Coordinates.NEW_YORK_LNG);

                // POST returns 202 ACCEPTED with ID
                String responseJson = mockMvc.perform(post("/webhook")
                                .header("Authorization", "Bearer " + Common.WEBHOOK_SECRET)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isAccepted())
                                .andExpect(jsonPath("$.id").exists())
                                .andExpect(jsonPath("$.status").value("PENDING"))
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                WebhookSubmissionResponseDto submissionResponse = objectMapper.readValue(responseJson,
                                WebhookSubmissionResponseDto.class);
                UUID requestId = submissionResponse.getId();

                // Wait for async processing to complete
                waitForProcessing(requestId, 10);

                // GET returns 200 OK with full response
                mockMvc.perform(get("/webhook/" + requestId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.key.lat").value(40.7128))
                                .andExpect(jsonPath("$.key.lng").value(-74.0060))
                                .andExpect(jsonPath("$.radiusMeters").value(500))
                                .andExpect(jsonPath("$.count").exists())
                                .andExpect(jsonPath("$.landmarks").isArray());
        }

        @Test
        void testWebhook_MissingAuth_Returns401() throws Exception {
                WebhookRequestDto request = TestFixtures.webhookRequest(
                                Coordinates.EIFFEL_TOWER_LAT,
                                Coordinates.EIFFEL_TOWER_LNG);

                mockMvc.perform(post("/webhook")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void testWebhook_InvalidToken_Returns401() throws Exception {
                WebhookRequestDto request = TestFixtures.webhookRequest(
                                Coordinates.EIFFEL_TOWER_LAT,
                                Coordinates.EIFFEL_TOWER_LNG);

                mockMvc.perform(post("/webhook")
                                .header("Authorization", "Bearer invalid")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void testWebhook_InvalidCoordinates_Returns400() throws Exception {
                WebhookRequestDto request = new WebhookRequestDto(
                                new BigDecimal("123.0"), // Invalid latitude
                                new BigDecimal("2.2945"));

                mockMvc.perform(post("/webhook")
                                .header("Authorization", "Bearer " + Common.WEBHOOK_SECRET)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void testLandmarks_ValidRequest_Returns200() throws Exception {
                // First, create a coordinate request (use different coordinates to avoid
                // conflict with seed data)
                CoordinateRequest coordinateRequest = TestFixtures.coordinateRequest(
                                Coordinates.NEW_YORK_LAT,
                                Coordinates.NEW_YORK_LNG,
                                Common.DEFAULT_RADIUS_METERS);
                coordinateRequest = coordinateRequestJpaRepository.saveAndFlush(coordinateRequest);

                mockMvc.perform(get("/landmarks")
                                .param("lat", Coordinates.NEW_YORK_LAT.toString())
                                .param("lng", Coordinates.NEW_YORK_LNG.toString()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.key.lat").value(40.7128))
                                .andExpect(jsonPath("$.key.lng").value(-74.0060))
                                .andExpect(jsonPath("$.key.radiusMeters").value(500))
                                .andExpect(jsonPath("$.landmarks").isArray());
        }

        @Test
        void testLandmarks_MissingLat_Returns400() throws Exception {
                mockMvc.perform(get("/landmarks")
                                .param("lng", "2.2945"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void testLandmarks_InvalidLat_Returns400() throws Exception {
                mockMvc.perform(get("/landmarks")
                                .param("lat", "123")
                                .param("lng", "2.2945"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void testIdempotency_DuplicateWebhook_ReturnsExistingResult() throws Exception {
                // Use coordinates that don't exist in seed data
                WebhookRequestDto request = TestFixtures.webhookRequest(
                                Coordinates.NEW_YORK_LAT,
                                Coordinates.NEW_YORK_LNG);

                String requestJson = objectMapper.writeValueAsString(request);

                // First call - returns ID immediately
                String firstResponseJson = mockMvc.perform(post("/webhook")
                                .header("Authorization", "Bearer " + Common.WEBHOOK_SECRET)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson))
                                .andExpect(status().isAccepted())
                                .andExpect(jsonPath("$.id").exists())
                                .andExpect(jsonPath("$.status").value("PENDING"))
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                WebhookSubmissionResponseDto firstSubmission = objectMapper.readValue(firstResponseJson,
                                WebhookSubmissionResponseDto.class);
                UUID firstRequestId = firstSubmission.getId();

                // Wait for first request to complete
                waitForProcessing(firstRequestId, 10);

                // Second call with same coordinates - should return same ID
                String secondResponseJson = mockMvc.perform(post("/webhook")
                                .header("Authorization", "Bearer " + Common.WEBHOOK_SECRET)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson))
                                .andExpect(status().isAccepted())
                                .andExpect(jsonPath("$.id").exists())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                WebhookSubmissionResponseDto secondSubmission = objectMapper.readValue(secondResponseJson,
                                WebhookSubmissionResponseDto.class);
                UUID secondRequestId = secondSubmission.getId();

                // Verify both requests return the same ID (idempotency)
                assertThat(secondRequestId).isEqualTo(firstRequestId);

                // Verify only one coordinate request exists for these coordinates
                // (Note: count() returns all requests, so we check it's at least 1)
                long count = coordinateRequestRepository.count();
                assertThat(count).isGreaterThanOrEqualTo(1);

                // Verify GET returns landmarks for both IDs
                mockMvc.perform(get("/webhook/" + firstRequestId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.landmarks").isArray());

                mockMvc.perform(get("/webhook/" + secondRequestId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.landmarks").isArray());
        }

        @Test
        void testCacheBehavior_FirstGetFromDb_SecondGetFromCache() throws Exception {
                // Clear caches before test to ensure clean state
                org.springframework.cache.Cache landmarksCache = cacheManager.getCache("landmarks");
                if (landmarksCache != null) {
                        landmarksCache.clear();
                }
                org.springframework.cache.Cache coordinateRequestsCache = cacheManager.getCache("coordinateRequests");
                if (coordinateRequestsCache != null) {
                        coordinateRequestsCache.clear();
                }

                // Create a coordinate request with landmarks (use different coordinates to
                // avoid conflict with seed data)
                CoordinateRequest coordinateRequest = TestFixtures.coordinateRequest(
                                Coordinates.NEW_YORK_LAT,
                                Coordinates.NEW_YORK_LNG,
                                Common.DEFAULT_RADIUS_METERS);
                coordinateRequest = coordinateRequestJpaRepository.saveAndFlush(coordinateRequest);

                // First GET - should return from DB
                String firstResponse = mockMvc.perform(get("/landmarks")
                                .param("lat", Coordinates.NEW_YORK_LAT.toString())
                                .param("lng", Coordinates.NEW_YORK_LNG.toString()))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                LandmarksQueryResponseDto firstResult = objectMapper.readValue(firstResponse,
                                LandmarksQueryResponseDto.class);
                assertThat(firstResult.getSource()).isEqualTo("db");

                // Second GET - should return from cache
                String secondResponse = mockMvc.perform(get("/landmarks")
                                .param("lat", Coordinates.NEW_YORK_LAT.toString())
                                .param("lng", Coordinates.NEW_YORK_LNG.toString()))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                LandmarksQueryResponseDto secondResult = objectMapper.readValue(secondResponse,
                                LandmarksQueryResponseDto.class);
                assertThat(secondResult.getSource()).isEqualTo("cache");
        }
}
