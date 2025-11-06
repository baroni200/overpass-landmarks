package com.overpass.landmarks.infrastructure.messaging.consumer;

import com.overpass.landmarks.application.port.out.CoordinateRequestRepository;
import com.overpass.landmarks.application.port.out.LandmarkRepository;
import com.overpass.landmarks.application.mapper.LandmarkMapper;
import com.overpass.landmarks.api.dto.LandmarkResponseDto;
import com.overpass.landmarks.domain.model.*;
import com.overpass.landmarks.domain.policy.CoordinateTransformer;
import com.overpass.landmarks.infrastructure.http.OverpassClient;
import com.overpass.landmarks.infrastructure.messaging.dto.WebhookProcessingMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachePut;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Kafka consumer for processing webhook requests asynchronously.
 * Handles cache checking, DB access, external API calls, and data persistence.
 */
@Service
public class WebhookProcessingConsumer {

    private static final Logger logger = LoggerFactory.getLogger(WebhookProcessingConsumer.class);

    private final CoordinateRequestRepository coordinateRequestRepository;
    private final LandmarkRepository landmarkRepository;
    private final OverpassClient overpassClient;
    private final LandmarkMapper landmarkMapper;
    private final CacheManager cacheManager;
    private final CoordinateTransformer coordinateTransformer;
    private final int queryRadiusMeters;
    private final Duration cacheExpirationDuration;

    public WebhookProcessingConsumer(
            CoordinateRequestRepository coordinateRequestRepository,
            LandmarkRepository landmarkRepository,
            OverpassClient overpassClient,
            LandmarkMapper landmarkMapper,
            CacheManager cacheManager,
            CoordinateTransformer coordinateTransformer,
            @Value("${app.overpass.query-radius-meters:500}") int queryRadiusMeters,
            @Value("${app.overpass.cache-expiration-days:60}") int cacheExpirationDays) {
        this.coordinateRequestRepository = coordinateRequestRepository;
        this.landmarkRepository = landmarkRepository;
        this.overpassClient = overpassClient;
        this.landmarkMapper = landmarkMapper;
        this.cacheManager = cacheManager;
        this.coordinateTransformer = coordinateTransformer;
        this.queryRadiusMeters = queryRadiusMeters;
        this.cacheExpirationDuration = Duration.ofDays(cacheExpirationDays);
    }

    @KafkaListener(
            topics = "${app.kafka.topic.webhook-processing:webhook-processing}",
            groupId = "${spring.kafka.consumer.group-id:webhook-processor-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void processWebhookMessage(WebhookProcessingMessage message, Acknowledgment acknowledgment) {
        UUID requestId = message.getRequestId();
        BigDecimal lat = message.getLat();
        BigDecimal lng = message.getLng();

        try {
            logger.info("Processing webhook message from Kafka for request ID: {}", requestId);

            // Retrieve the pending request
            Optional<CoordinateRequest> requestOpt = coordinateRequestRepository.findByIdNotDeleted(requestId);
            if (requestOpt.isEmpty()) {
                logger.error("Request not found for ID: {}", requestId);
                acknowledgment.acknowledge();
                return;
            }

            CoordinateRequest coordinateRequest = requestOpt.get();

            // Ensure it's still pending
            if (coordinateRequest.getStatus() != RequestStatus.PENDING) {
                logger.info("Request {} is already processed with status: {}",
                    requestId, coordinateRequest.getStatus());
                acknowledgment.acknowledge();
                return;
            }

            TransformedCoordinates transformed = new TransformedCoordinates(
                coordinateRequest.getKeyLat(),
                coordinateRequest.getKeyLng());

            // Step 1: Check cache first
            String cacheKey = buildCacheKey(transformed);
            Optional<List<LandmarkResponseDto>> cachedLandmarks = getFromCache(cacheKey);

            List<Landmark> landmarks;
            if (cachedLandmarks.isPresent()) {
                logger.info("Cache hit for key: {}, skipping Overpass API call for request ID: {}",
                    cacheKey, requestId);

                // Cache found - load landmarks from DB
                landmarks = landmarkRepository.findByCoordinateRequestId(requestId);

                if (landmarks.isEmpty()) {
                    logger.warn("Cache hit but no landmarks in DB for request ID: {}, will query Overpass API", requestId);
                    // Fall through to query Overpass API
                } else {
                    // Update status based on landmarks
                    coordinateRequest.setStatus(landmarks.isEmpty() ? RequestStatus.EMPTY : RequestStatus.FOUND);
                    coordinateRequest = coordinateRequestRepository.save(coordinateRequest);

                    logger.info("Completed processing for request ID: {}, status: {} (from cache)",
                        requestId, coordinateRequest.getStatus());
                    acknowledgment.acknowledge();
                    return;
                }
            }

            // Step 2: Cache miss - Check DB for existing landmarks
            Optional<CoordinateRequest> existingDbRequest = coordinateRequestRepository
                .findByKeyLatAndKeyLngAndRadiusMeters(
                    transformed.getLat(),
                    transformed.getLng(),
                    queryRadiusMeters);

            if (existingDbRequest.isPresent() && existingDbRequest.get().getStatus() != RequestStatus.PENDING) {
                CoordinateRequest dbRequest = existingDbRequest.get();

                // Check if request is expired
                OffsetDateTime expirationTime = dbRequest.getRequestedAt().plus(cacheExpirationDuration);
                boolean isExpired = OffsetDateTime.now().isAfter(expirationTime);

                if (!isExpired) {
                    // DB has valid data - load landmarks from DB
                    logger.info("Cache miss but found valid data in DB for key: {}, loading from DB for request ID: {} (skipping Overpass API)",
                        cacheKey, requestId);
                    landmarks = landmarkRepository.findByCoordinateRequestId(dbRequest.getId());

                    if (!landmarks.isEmpty()) {
                        // Update current request status
                        coordinateRequest.setStatus(RequestStatus.FOUND);
                        coordinateRequest = coordinateRequestRepository.save(coordinateRequest);

                        // Populate cache for next time
                        populateCache(transformed, landmarks, queryRadiusMeters);

                        logger.info("Completed processing for request ID: {}, status: {} (from DB, skipping Overpass API)",
                            requestId, coordinateRequest.getStatus());
                        acknowledgment.acknowledge();
                        return;
                    }
                }
            }

            // Step 3: Cache miss AND DB miss (or expired) - Query Overpass API
            try {
                logger.info("Cache miss and DB miss (or expired) for key: {}, querying Overpass API for request ID: {}",
                    cacheKey, requestId);
                List<OverpassClient.OverpassLandmark> overpassLandmarks = overpassClient
                    .queryLandmarks(transformed.getLat(), transformed.getLng(), queryRadiusMeters);

                if (overpassLandmarks.isEmpty()) {
                    coordinateRequest.setStatus(RequestStatus.EMPTY);
                    logger.info("No landmarks found for coordinates: {}", transformed);
                } else {
                    coordinateRequest.setStatus(RequestStatus.FOUND);
                    logger.info("Found {} landmarks for coordinates: {}", overpassLandmarks.size(), transformed);
                }

                coordinateRequest = coordinateRequestRepository.save(coordinateRequest);
                final CoordinateRequest finalCoordinateRequest = coordinateRequest;

                // Step 4: Persist landmarks (check for existing landmarks to avoid duplicates)
                landmarks = new java.util.ArrayList<>();
                for (OverpassClient.OverpassLandmark overpassLandmark : overpassLandmarks) {
                    // Check if landmark already exists globally (by osm_type, osm_id)
                    Optional<Landmark> existingLandmarkOpt = landmarkRepository.findByOsmTypeAndOsmId(
                            overpassLandmark.getOsmType(),
                            overpassLandmark.getOsmId());
                    
                    if (existingLandmarkOpt.isPresent()) {
                        Landmark existingLandmark = existingLandmarkOpt.get();
                        // If landmark already exists and belongs to this request, reuse it
                        // If it belongs to another request, we can't create a duplicate due to unique constraint
                        // In this case, we still include it in the list for this request
                        if (existingLandmark.getCoordinateRequest().getId().equals(finalCoordinateRequest.getId())) {
                            logger.debug("Landmark {} {} already exists for this request, reusing", 
                                    overpassLandmark.getOsmType(), overpassLandmark.getOsmId());
                        } else {
                            logger.debug("Landmark {} {} already exists for another request ({}), reusing to avoid duplicate constraint", 
                                    overpassLandmark.getOsmType(), overpassLandmark.getOsmId(), 
                                    existingLandmark.getCoordinateRequest().getId());
                        }
                        landmarks.add(existingLandmark);
                    } else {
                        // Create new landmark
                        Landmark landmark = new Landmark(
                                finalCoordinateRequest,
                                overpassLandmark.getOsmType(),
                                overpassLandmark.getOsmId(),
                                overpassLandmark.getLat(),
                                overpassLandmark.getLng());
                        landmark.setName(overpassLandmark.getName());
                        landmark.setTags(overpassLandmark.getTags());
                        landmarks.add(landmarkRepository.save(landmark));
                    }
                }

            } catch (OverpassClient.OverpassException e) {
                logger.error("Overpass API error for request ID: {}", requestId, e);
                coordinateRequest.setStatus(RequestStatus.ERROR);
                coordinateRequest.setErrorMessage(e.getMessage());
                coordinateRequestRepository.save(coordinateRequest);
                acknowledgment.acknowledge();
                return;
            }

            // Step 5: Write-through cache
            populateCache(transformed, landmarks, queryRadiusMeters);

            logger.info("Completed processing for request ID: {}, status: {}",
                requestId, coordinateRequest.getStatus());
            acknowledgment.acknowledge();

        } catch (Exception e) {
            logger.error("Unexpected error processing webhook message from Kafka for request ID: {}", requestId, e);
            // Update request status to ERROR
            try {
                Optional<CoordinateRequest> requestOpt = coordinateRequestRepository.findByIdNotDeleted(requestId);
                if (requestOpt.isPresent()) {
                    CoordinateRequest coordinateRequest = requestOpt.get();
                    coordinateRequest.setStatus(RequestStatus.ERROR);
                    coordinateRequest.setErrorMessage(e.getMessage());
                    coordinateRequestRepository.save(coordinateRequest);
                }
            } catch (Exception saveException) {
                logger.error("Failed to update request status to ERROR", saveException);
            }
            // Don't acknowledge on error - let Kafka retry
            throw new RuntimeException("Failed to process webhook message", e);
        }
    }

    /**
     * Build cache key for landmarks.
     */
    private String buildCacheKey(TransformedCoordinates transformed) {
        return transformed.getLat().toString() + ":" + transformed.getLng().toString() + ":" + queryRadiusMeters;
    }

    /**
     * Get landmarks from cache.
     */
    private Optional<List<LandmarkResponseDto>> getFromCache(String cacheKey) {
        Cache cache = cacheManager.getCache("landmarks");
        if (cache != null) {
            Cache.ValueWrapper wrapper = cache.get(cacheKey);
            if (wrapper != null) {
                Object cachedValue = wrapper.get();
                if (cachedValue instanceof List<?> cachedList) {
                    @SuppressWarnings("unchecked")
                    List<LandmarkResponseDto> cachedLandmarks = (List<LandmarkResponseDto>) cachedList;
                    return Optional.of(cachedLandmarks);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Populate cache with landmarks (write-through strategy).
     */
    @CachePut(value = "landmarks", key = "#transformed.lat.toString() + ':' + #transformed.lng.toString() + ':' + #queryRadiusMeters")
    private List<LandmarkResponseDto> populateCache(TransformedCoordinates transformed, List<Landmark> landmarks,
        int queryRadiusMeters) {
        return landmarks.stream()
            .map(landmarkMapper::toDto)
            .toList();
    }
}

