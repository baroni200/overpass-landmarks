package com.overpass.landmarks.application.service;

import com.overpass.landmarks.application.dto.LandmarkResponseDto;
import com.overpass.landmarks.application.dto.LandmarksQueryResponseDto;
import com.overpass.landmarks.domain.model.Coordinates;
import com.overpass.landmarks.domain.model.Landmark;
import com.overpass.landmarks.domain.model.TransformedCoordinates;
import com.overpass.landmarks.domain.repository.CoordinateRequestRepository;
import com.overpass.landmarks.domain.repository.LandmarkRepository;
import com.overpass.landmarks.domain.service.CoordinateTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Application service for querying landmarks.
 * Implements cache-first strategy with database fallback.
 */
@Service
public class LandmarkQueryService {

    private static final Logger logger = LoggerFactory.getLogger(LandmarkQueryService.class);

    private final CoordinateTransformer coordinateTransformer;
    private final CoordinateRequestRepository coordinateRequestRepository;
    private final LandmarkRepository landmarkRepository;
    private final CacheManager cacheManager;
    private final int queryRadiusMeters;

    public LandmarkQueryService(
        CoordinateTransformer coordinateTransformer,
        CoordinateRequestRepository coordinateRequestRepository,
        LandmarkRepository landmarkRepository,
        CacheManager cacheManager,
        @Value("${app.overpass.query-radius-meters:500}") int queryRadiusMeters
    ) {
        this.coordinateTransformer = coordinateTransformer;
        this.coordinateRequestRepository = coordinateRequestRepository;
        this.landmarkRepository = landmarkRepository;
        this.cacheManager = cacheManager;
        this.queryRadiusMeters = queryRadiusMeters;
    }

    /**
     * Query landmarks by coordinates.
     * Strategy: Cache-first → DB fallback → populate cache
     * 
     * @param lat Latitude
     * @param lng Longitude
     * @return Query response with landmarks and source indicator
     */
    @Transactional(readOnly = true)
    public LandmarksQueryResponseDto queryLandmarks(BigDecimal lat, BigDecimal lng) {
        // Step 1: Transform coordinates (same rule as webhook)
        Coordinates coordinates = new Coordinates(lat, lng);
        TransformedCoordinates transformed = coordinateTransformer.transform(coordinates);

        logger.debug("Querying landmarks for coordinates: {} -> {}", coordinates, transformed);

        // Step 2: Try cache first (cache-aside pattern)
        String cacheKey = buildCacheKey(transformed);
        Cache cache = cacheManager.getCache("landmarks");
        if (cache != null) {
            Cache.ValueWrapper wrapper = cache.get(cacheKey);
            if (wrapper != null) {
                Object cachedValue = wrapper.get();
                if (cachedValue instanceof List<?> cachedList) {
                    // Type-safe casting with pattern matching (Java 16+)
                    @SuppressWarnings("unchecked")
                    List<LandmarkResponseDto> cachedLandmarks = 
                        (List<LandmarkResponseDto>) cachedList;
                    logger.debug("Cache hit for key: {}", cacheKey);
                    return buildResponse(transformed, cachedLandmarks, "cache");
                }
            }
        }
        logger.debug("Cache miss for key: {}", cacheKey);

        // Step 3: Cache miss - query database
        Optional<com.overpass.landmarks.domain.model.CoordinateRequest> request = coordinateRequestRepository
            .findByKeyLatAndKeyLngAndRadiusMeters(
                transformed.getLat(),
                transformed.getLng(),
                queryRadiusMeters
            );

        if (request.isEmpty()) {
            logger.debug("No data found in database for key: {}:{}", transformed.getLat(), transformed.getLng());
            return buildResponse(transformed, List.of(), "none");
        }

        // Step 4: Load landmarks from database
        List<Landmark> landmarks = landmarkRepository.findByCoordinateRequestId(request.get().getId());
        List<LandmarkResponseDto> landmarkDtos = landmarks.stream()
            .map(this::toDto)
            .toList();

        // Step 5: Populate cache for next time
        populateCache(transformed, landmarkDtos, queryRadiusMeters);

        return buildResponse(transformed, landmarkDtos, "db");
    }

    /**
     * Populate cache (write-through on GET).
     */
    private void populateCache(TransformedCoordinates transformed, List<LandmarkResponseDto> landmarks, int queryRadiusMeters) {
        Cache cache = cacheManager.getCache("landmarks");
        if (cache != null) {
            String cacheKey = buildCacheKey(transformed);
            cache.put(cacheKey, landmarks);
            logger.debug("Cache populated for key: {}", cacheKey);
        }
    }

    /**
     * Build cache key for landmarks.
     */
    private String buildCacheKey(TransformedCoordinates transformed) {
        return transformed.getLat().toString() + ":" + transformed.getLng().toString() + ":" + queryRadiusMeters;
    }

    private LandmarksQueryResponseDto buildResponse(
        TransformedCoordinates transformed,
        List<LandmarkResponseDto> landmarks,
        String source
    ) {
        LandmarksQueryResponseDto.KeyDto key = new LandmarksQueryResponseDto.KeyDto(
            transformed.getLat(),
            transformed.getLng(),
            queryRadiusMeters
        );
        return new LandmarksQueryResponseDto(key, source, landmarks);
    }

    private LandmarkResponseDto toDto(Landmark landmark) {
        return new LandmarkResponseDto(
            landmark.getId(),
            landmark.getName(),
            landmark.getOsmType().name(),
            landmark.getOsmId(),
            landmark.getLat(),
            landmark.getLng(),
            landmark.getTags()
        );
    }
}

