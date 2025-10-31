package com.overpass.landmarks;

import com.overpass.landmarks.application.dto.LandmarksQueryResponseDto;
import com.overpass.landmarks.application.dto.WebhookRequestDto;
import com.overpass.landmarks.application.dto.WebhookResponseDto;
import com.overpass.landmarks.domain.model.CoordinateRequest;
import com.overpass.landmarks.domain.model.RequestStatus;
import com.overpass.landmarks.domain.repository.CoordinateRequestRepository;
import com.overpass.landmarks.domain.repository.LandmarkRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("local")
@Transactional
class WebhookIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CoordinateRequestRepository coordinateRequestRepository;

    @Autowired
    private LandmarkRepository landmarkRepository;

    private MockMvc mockMvc;
    private static final String WEBHOOK_SECRET = "supersecret";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void testWebhook_ValidRequest_Returns200() throws Exception {
        WebhookRequestDto request = new WebhookRequestDto(
            new BigDecimal("48.8584"),
            new BigDecimal("2.2945")
        );

        mockMvc.perform(post("/webhook")
                .header("Authorization", "Bearer " + WEBHOOK_SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.key.lat").value(48.8584))
            .andExpect(jsonPath("$.key.lng").value(2.2945))
            .andExpect(jsonPath("$.radiusMeters").value(500));
    }

    @Test
    void testWebhook_MissingAuth_Returns401() throws Exception {
        WebhookRequestDto request = new WebhookRequestDto(
            new BigDecimal("48.8584"),
            new BigDecimal("2.2945")
        );

        mockMvc.perform(post("/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void testWebhook_InvalidToken_Returns401() throws Exception {
        WebhookRequestDto request = new WebhookRequestDto(
            new BigDecimal("48.8584"),
            new BigDecimal("2.2945")
        );

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
            new BigDecimal("2.2945")
        );

        mockMvc.perform(post("/webhook")
                .header("Authorization", "Bearer " + WEBHOOK_SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testLandmarks_ValidRequest_Returns200() throws Exception {
        // First, create a coordinate request
        CoordinateRequest coordinateRequest = new CoordinateRequest(
            new BigDecimal("48.8584"),
            new BigDecimal("2.2945"),
            500
        );
        coordinateRequest.setStatus(RequestStatus.FOUND);
        coordinateRequest = coordinateRequestRepository.save(coordinateRequest);

        mockMvc.perform(get("/landmarks")
                .param("lat", "48.8584")
                .param("lng", "2.2945"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.key.lat").value(48.8584))
            .andExpect(jsonPath("$.key.lng").value(2.2945))
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
        WebhookRequestDto request = new WebhookRequestDto(
            new BigDecimal("48.8584"),
            new BigDecimal("2.2945")
        );

        String requestJson = objectMapper.writeValueAsString(request);

        // First call
        String firstResponse = mockMvc.perform(post("/webhook")
                .header("Authorization", "Bearer " + WEBHOOK_SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        // Second call with same coordinates
        String secondResponse = mockMvc.perform(post("/webhook")
                .header("Authorization", "Bearer " + WEBHOOK_SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        // Verify only one coordinate request exists
        long count = coordinateRequestRepository.count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void testCacheBehavior_FirstGetFromDb_SecondGetFromCache() throws Exception {
        // Create a coordinate request with landmarks
        CoordinateRequest coordinateRequest = new CoordinateRequest(
            new BigDecimal("48.8584"),
            new BigDecimal("2.2945"),
            500
        );
        coordinateRequest.setStatus(RequestStatus.FOUND);
        coordinateRequest = coordinateRequestRepository.save(coordinateRequest);

        // First GET - should return from DB
        String firstResponse = mockMvc.perform(get("/landmarks")
                .param("lat", "48.8584")
                .param("lng", "2.2945"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        LandmarksQueryResponseDto firstResult = objectMapper.readValue(firstResponse, LandmarksQueryResponseDto.class);
        assertThat(firstResult.getSource()).isEqualTo("db");

        // Second GET - should return from cache
        String secondResponse = mockMvc.perform(get("/landmarks")
                .param("lat", "48.8584")
                .param("lng", "2.2945"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        LandmarksQueryResponseDto secondResult = objectMapper.readValue(secondResponse, LandmarksQueryResponseDto.class);
        assertThat(secondResult.getSource()).isEqualTo("cache");
    }
}

