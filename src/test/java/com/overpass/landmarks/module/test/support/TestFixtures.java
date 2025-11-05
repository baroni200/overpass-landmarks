package com.overpass.landmarks.module.test.support;

import com.overpass.landmarks.api.dto.LandmarkResponseDto;
import com.overpass.landmarks.api.dto.LandmarksQueryResponseDto;
import com.overpass.landmarks.api.dto.WebhookRequestDto;
import com.overpass.landmarks.api.dto.WebhookResponseDto;
import com.overpass.landmarks.domain.model.CoordinateRequest;
import com.overpass.landmarks.domain.model.Landmark;
import com.overpass.landmarks.domain.model.OsmType;
import com.overpass.landmarks.domain.model.RequestStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Test fixtures for creating test data.
 * Provides builders and factory methods for common test scenarios.
 */
public class TestFixtures {

    private TestFixtures() {
        // Utility class
    }

    /**
     * Create a test coordinate request.
     */
    public static CoordinateRequest coordinateRequest(
            BigDecimal lat,
            BigDecimal lng,
            Integer radiusMeters,
            RequestStatus status) {
        CoordinateRequest request = new CoordinateRequest(lat, lng, radiusMeters);
        request.setStatus(status);
        return request;
    }

    /**
     * Create a test coordinate request with FOUND status.
     */
    public static CoordinateRequest coordinateRequest(BigDecimal lat, BigDecimal lng, Integer radiusMeters) {
        return coordinateRequest(lat, lng, radiusMeters, RequestStatus.FOUND);
    }

    /**
     * Create a test landmark.
     */
    public static Landmark landmark(
            CoordinateRequest coordinateRequest,
            OsmType osmType,
            Long osmId,
            BigDecimal lat,
            BigDecimal lng,
            String name,
            Map<String, Object> tags) {
        Landmark landmark = new Landmark(coordinateRequest, osmType, osmId, lat, lng);
        landmark.setName(name);
        landmark.setTags(tags);
        return landmark;
    }

    /**
     * Create a test landmark with minimal data.
     */
    public static Landmark landmark(
            CoordinateRequest coordinateRequest,
            OsmType osmType,
            Long osmId,
            BigDecimal lat,
            BigDecimal lng) {
        return new Landmark(coordinateRequest, osmType, osmId, lat, lng);
    }

    /**
     * Create a test webhook request DTO.
     */
    public static WebhookRequestDto webhookRequest(BigDecimal lat, BigDecimal lng) {
        return new WebhookRequestDto(lat, lng);
    }

    /**
     * Create a test webhook response DTO.
     */
    public static WebhookResponseDto webhookResponse(
            BigDecimal lat,
            BigDecimal lng,
            Integer count,
            Integer radiusMeters,
            List<LandmarkResponseDto> landmarks) {
        WebhookResponseDto.KeyDto key = new WebhookResponseDto.KeyDto(lat, lng);
        return new WebhookResponseDto(key, count, radiusMeters, landmarks);
    }

    /**
     * Create a test landmark response DTO.
     */
    public static LandmarkResponseDto landmarkResponse(
            UUID id,
            String name,
            OsmType osmType,
            Long osmId,
            BigDecimal lat,
            BigDecimal lng,
            Map<String, Object> tags) {
        return new LandmarkResponseDto(id, name, osmType.name(), osmId, lat, lng, tags);
    }

    /**
     * Create a test landmarks query response DTO.
     */
    public static LandmarksQueryResponseDto landmarksQueryResponse(
            BigDecimal lat,
            BigDecimal lng,
            Integer radiusMeters,
            String source,
            List<LandmarkResponseDto> landmarks) {
        LandmarksQueryResponseDto.KeyDto key = new LandmarksQueryResponseDto.KeyDto(lat, lng, radiusMeters);
        return new LandmarksQueryResponseDto(key, source, landmarks);
    }

    /**
     * Common test coordinates.
     */
    public static class Coordinates {
        public static final BigDecimal EIFFEL_TOWER_LAT = new BigDecimal("48.8584");
        public static final BigDecimal EIFFEL_TOWER_LNG = new BigDecimal("2.2945");
        public static final BigDecimal NOTRE_DAME_LAT = new BigDecimal("48.8530");
        public static final BigDecimal NOTRE_DAME_LNG = new BigDecimal("2.3499");
        public static final BigDecimal NEW_YORK_LAT = new BigDecimal("40.7128");
        public static final BigDecimal NEW_YORK_LNG = new BigDecimal("-74.0060");
    }

    /**
     * Common test data.
     */
    public static class Common {
        public static final String WEBHOOK_SECRET = "supersecret";
        public static final Integer DEFAULT_RADIUS_METERS = 500;
        public static final Long EIFFEL_TOWER_OSM_ID = 5013364L;
        public static final Long NOTRE_DAME_OSM_ID = 5013000L;
        
        public static Map<String, Object> eiffelTowerTags() {
            return Map.of(
                    "tourism", "attraction",
                    "name", "Eiffel Tower",
                    "wikidata", "Q243"
            );
        }
        
        public static Map<String, Object> notreDameTags() {
            return Map.of(
                    "tourism", "attraction",
                    "name", "Notre-Dame de Paris",
                    "wikipedia", "en:Notre-Dame de Paris",
                    "historic", "cathedral"
            );
        }
    }
}

