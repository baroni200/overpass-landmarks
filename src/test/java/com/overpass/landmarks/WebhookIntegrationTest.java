package com.overpass.landmarks;

import com.overpass.landmarks.api.dto.LandmarksQueryResponseDto;
import com.overpass.landmarks.api.dto.WebhookRequestDto;
import com.overpass.landmarks.application.port.out.CoordinateRequestRepository;
import com.overpass.landmarks.domain.model.CoordinateRequest;
import com.overpass.landmarks.infrastructure.persistence.CoordinateRequestJpaRepository;
import com.overpass.landmarks.module.test.support.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static com.overpass.landmarks.module.test.support.TestFixtures.Coordinates;
import static com.overpass.landmarks.module.test.support.TestFixtures.Common;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
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

    @Test
    void testWebhook_ValidRequest_Returns200() throws Exception {
        WebhookRequestDto request = TestFixtures.webhookRequest(
                Coordinates.EIFFEL_TOWER_LAT,
                Coordinates.EIFFEL_TOWER_LNG);

        mockMvc.perform(post("/webhook")
                .header("Authorization", "Bearer " + Common.WEBHOOK_SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key.lat").value(48.8584))
                .andExpect(jsonPath("$.key.lng").value(2.2945))
                .andExpect(jsonPath("$.radiusMeters").value(500))
                .andExpect(jsonPath("$.count").exists())
                .andExpect(jsonPath("$.landmarks").isArray())
                .andExpect(jsonPath("$.landmarks").isNotEmpty())
                .andExpect(jsonPath("$.landmarks[0].name").exists())
                .andExpect(jsonPath("$.landmarks[0].lat").exists())
                .andExpect(jsonPath("$.landmarks[0].lng").exists())
                .andExpect(jsonPath("$.landmarks[0].tags").exists());
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
        WebhookRequestDto request = TestFixtures.webhookRequest(
                Coordinates.EIFFEL_TOWER_LAT,
                Coordinates.EIFFEL_TOWER_LNG);

        String requestJson = objectMapper.writeValueAsString(request);

        // First call
        String firstResponse = mockMvc.perform(post("/webhook")
                .header("Authorization", "Bearer " + Common.WEBHOOK_SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.landmarks").isArray())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Second call with same coordinates
        String secondResponse = mockMvc.perform(post("/webhook")
                .header("Authorization", "Bearer " + Common.WEBHOOK_SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.landmarks").isArray())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Verify only one coordinate request exists
        long count = coordinateRequestRepository.count();
        assertThat(count).isEqualTo(1);

        // Verify both responses contain landmarks
        assertThat(firstResponse).contains("\"landmarks\"");
        assertThat(secondResponse).contains("\"landmarks\"");
    }

    @Test
    void testCacheBehavior_FirstGetFromDb_SecondGetFromCache() throws Exception {
        // Clear cache before test to ensure clean state
        org.springframework.cache.Cache cache = cacheManager.getCache("landmarks");
        if (cache != null) {
            cache.clear();
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

        LandmarksQueryResponseDto firstResult = objectMapper.readValue(firstResponse, LandmarksQueryResponseDto.class);
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
