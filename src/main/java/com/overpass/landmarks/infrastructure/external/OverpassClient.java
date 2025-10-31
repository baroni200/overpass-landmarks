package com.overpass.landmarks.infrastructure.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.overpass.landmarks.domain.model.Landmark;
import com.overpass.landmarks.domain.model.OsmType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for interacting with the Overpass API.
 * Handles query building, HTTP requests, and response parsing.
 */
@Service
public class OverpassClient {

    private static final Logger logger = LoggerFactory.getLogger(OverpassClient.class);
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiUrl;
    private final int timeoutSeconds;

    public OverpassClient(
        WebClient.Builder webClientBuilder,
        ObjectMapper objectMapper,
        @Value("${app.overpass.api-url}") String apiUrl,
        @Value("${app.overpass.timeout-seconds:30}") int timeoutSeconds
    ) {
        this.objectMapper = objectMapper;
        this.apiUrl = apiUrl;
        this.timeoutSeconds = timeoutSeconds;
        this.webClient = webClientBuilder
            .baseUrl(apiUrl)
            .build();
    }

    /**
     * Query Overpass API for landmarks near the given coordinates.
     * 
     * @param lat Latitude
     * @param lng Longitude
     * @param radiusMeters Search radius in meters
     * @return List of landmarks found
     * @throws OverpassException if the API call fails or times out
     */
    public List<OverpassLandmark> queryLandmarks(BigDecimal lat, BigDecimal lng, int radiusMeters) {
        String query = buildQuery(lat, lng, radiusMeters);
        logger.debug("Executing Overpass query: {}", query);

        try {
            String responseBody = webClient.post()
                .uri("/interpreter")
                .bodyValue(query)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .retryWhen(Retry.fixedDelay(2, Duration.ofSeconds(1))
                    .filter(throwable -> throwable instanceof WebClientException))
                .block();

            return parseResponse(responseBody);
        } catch (WebClientResponseException e) {
            logger.error("Overpass API returned error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new OverpassException("Overpass API returned " + e.getStatusCode(), e);
        } catch (WebClientException e) {
            logger.error("Failed to connect to Overpass API", e);
            throw new OverpassException("Failed to connect to Overpass API", e);
        } catch (Exception e) {
            logger.error("Unexpected error querying Overpass API", e);
            throw new OverpassException("Unexpected error querying Overpass API", e);
        }
    }

    /**
     * Build Overpass QL query string.
     */
    private String buildQuery(BigDecimal lat, BigDecimal lng, int radiusMeters) {
        return String.format(
            "[out:json];" +
            "(" +
            "  way[\"tourism\"=\"attraction\"](around:%d,%.6f,%.6f);" +
            "  relation[\"tourism\"=\"attraction\"](around:%d,%.6f,%.6f);" +
            ");" +
            "out center;",
            radiusMeters, lat.doubleValue(), lng.doubleValue(),
            radiusMeters, lat.doubleValue(), lng.doubleValue()
        );
    }

    /**
     * Parse Overpass JSON response into list of landmarks.
     */
    private List<OverpassLandmark> parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode elements = root.get("elements");
            
            if (elements == null || !elements.isArray()) {
                logger.warn("Overpass response missing elements array");
                return List.of();
            }

            List<OverpassLandmark> landmarks = new ArrayList<>();
            for (JsonNode element : elements) {
                try {
                    OverpassLandmark landmark = parseElement(element);
                    if (landmark != null) {
                        landmarks.add(landmark);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse element: {}", element, e);
                }
            }

            logger.info("Parsed {} landmarks from Overpass response", landmarks.size());
            return landmarks;
        } catch (Exception e) {
            logger.error("Failed to parse Overpass response", e);
            throw new OverpassException("Failed to parse Overpass response", e);
        }
    }

    /**
     * Parse a single Overpass element into a landmark.
     */
    private OverpassLandmark parseElement(JsonNode element) {
        String type = element.get("type").asText();
        Long osmId = element.get("id").asLong();
        
        // Get center coordinates (preferred) or fallback to lat/lng
        BigDecimal lat, lng;
        JsonNode center = element.get("center");
        if (center != null) {
            lat = BigDecimal.valueOf(center.get("lat").asDouble());
            lng = BigDecimal.valueOf(center.get("lon").asDouble());
        } else {
            lat = BigDecimal.valueOf(element.get("lat").asDouble());
            lng = BigDecimal.valueOf(element.get("lon").asDouble());
        }

        // Extract name and tags
        JsonNode tags = element.get("tags");
        String name = null;
        Map<String, Object> tagMap = new HashMap<>();
        
        if (tags != null) {
            name = tags.has("name") ? tags.get("name").asText() : null;
            tags.fields().forEachRemaining(entry -> {
                tagMap.put(entry.getKey(), entry.getValue().asText());
            });
        }

        OsmType osmType = switch (type.toLowerCase()) {
            case "way" -> OsmType.way;
            case "relation" -> OsmType.relation;
            case "node" -> OsmType.node;
            default -> {
                logger.warn("Unknown OSM type: {}", type);
                yield null;
            }
        };

        if (osmType == null) {
            return null;
        }

        return new OverpassLandmark(osmType, osmId, name, lat, lng, tagMap);
    }

    /**
     * DTO representing a landmark from Overpass API.
     */
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class OverpassLandmark {
        private final OsmType osmType;
        private final Long osmId;
        private final String name;
        private final BigDecimal lat;
        private final BigDecimal lng;
        private final Map<String, Object> tags;
    }

    /**
     * Exception thrown when Overpass API calls fail.
     */
    public static class OverpassException extends RuntimeException {
        public OverpassException(String message) {
            super(message);
        }

        public OverpassException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

