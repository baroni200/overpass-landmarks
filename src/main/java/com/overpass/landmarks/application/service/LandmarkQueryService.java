package com.overpass.landmarks.application.service;

import com.overpass.landmarks.api.dto.LandmarkResponseDto;
import com.overpass.landmarks.api.dto.LandmarksQueryResponseDto;
import com.overpass.landmarks.application.mapper.LandmarkMapper;
import com.overpass.landmarks.application.port.in.QueryLandmarksUseCase;
import com.overpass.landmarks.application.port.out.CoordinateRequestRepository;
import com.overpass.landmarks.application.port.out.LandmarkRepository;
import com.overpass.landmarks.domain.model.CoordinateRequest;
import com.overpass.landmarks.domain.model.Coordinates;
import com.overpass.landmarks.domain.model.Landmark;
import com.overpass.landmarks.domain.model.TransformedCoordinates;
import com.overpass.landmarks.domain.policy.CoordinateTransformer;
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
public class LandmarkQueryService implements QueryLandmarksUseCase {

    private static final Logger logger = LoggerFactory.getLogger(LandmarkQueryService.class);

    private final CoordinateTransformer coordinateTransformer;
    private final CoordinateRequestRepository coordinateRequestRepository;
    private final LandmarkRepository landmarkRepository;
    private final CacheManager cacheManager;
    private final LandmarkMapper landmarkMapper;
    private final int queryRadiusMeters;

    public LandmarkQueryService(
            CoordinateTransformer coordinateTransformer,
            CoordinateRequestRepository coordinateRequestRepository,
            LandmarkRepository landmarkRepository,
            CacheManager cacheManager,
            LandmarkMapper landmarkMapper,
            @Value("${app.overpass.query-radius-meters:500}") int queryRadiusMeters) {
        this.coordinateTransformer = coordinateTransformer;
        this.coordinateRequestRepository = coordinateRequestRepository;
        this.landmarkRepository = landmarkRepository;
        this.cacheManager = cacheManager;
        this.landmarkMapper = landmarkMapper;
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
    @Override
    @Transactional(readOnly = true)
    public LandmarksQueryResponseDto queryLandmarks(BigDecimal lat, BigDecimal lng) {
        // Step 1: Transform coordinates (same rule as webhook)
        Coordinates coordinates = new Coordinates(lat, lng);
        TransformedCoordinates transformed = coordinateTransformer.transform(coordinates);

        logger.debug("Querying landmarks for coordinates: {} -> {}", coordinates, transformed);

        String cacheKey = buildCacheKey(transformed);

        // Step 2: Check CoordinateRequest cache first (parent entity - if it doesn't
        // exist, no landmarks exist)
        Optional<CoordinateRequest> request = getCoordinateRequestFromCache(transformed);

        if (request.isEmpty()) {
            // Step 3: CoordinateRequest cache miss - query database
            logger.debug("CoordinateRequest cache miss for key: {}", cacheKey);
            request = coordinateRequestRepository
                    .findByKeyLatAndKeyLngAndRadiusMeters(
                            transformed.getLat(),
                            transformed.getLng(),
                            queryRadiusMeters);

            // Step 4: Populate CoordinateRequest cache
            if (request.isPresent()) {
                putCoordinateRequestInCache(transformed, request.get());
            }
        } else {
            logger.debug("CoordinateRequest cache hit for key: {}", cacheKey);
        }

        // Step 5: Early return if CoordinateRequest doesn't exist
        if (request.isEmpty()) {
            logger.debug("No CoordinateRequest found for key: {}:{}", transformed.getLat(), transformed.getLng());
            return buildResponse(transformed, List.of(), "none");
        }

        // Step 6: Check landmarks cache (now that we know CoordinateRequest exists)
        try {
            Cache cache = cacheManager.getCache("landmarks");
            if (cache != null) {
                Cache.ValueWrapper wrapper = cache.get(cacheKey);
                if (wrapper != null) {
                    Object cachedValue = wrapper.get();
                    if (cachedValue instanceof List<?> cachedList) {
                        // Type-safe casting with pattern matching (Java 16+)
                        @SuppressWarnings("unchecked")
                        List<LandmarkResponseDto> cachedLandmarks = (List<LandmarkResponseDto>) cachedList;
                        logger.debug("Landmarks cache hit for key: {}", cacheKey);
                        return buildResponse(transformed, cachedLandmarks, "cache");
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to get landmarks from cache, continuing without cache: {}", e.getMessage());
        }
        logger.debug("Landmarks cache miss for key: {}", cacheKey);

        // Step 7: Load landmarks from database
        List<Landmark> landmarks = landmarkRepository.findByCoordinateRequestId(request.get().getId());
        List<LandmarkResponseDto> landmarkDtos = landmarks.stream()
                .map(landmarkMapper::toDto)
                .toList();

        // Step 8: Populate landmarks cache for next time
        populateCache(transformed, landmarkDtos, queryRadiusMeters);

        return buildResponse(transformed, landmarkDtos, "db");
    }

    /**
     * Populate cache (write-through on GET).
     * Gracefully handles cache unavailability (e.g., Redis connection failures).
     */
    private void populateCache(TransformedCoordinates transformed, List<LandmarkResponseDto> landmarks,
            int queryRadiusMeters) {
        try {
            Cache cache = cacheManager.getCache("landmarks");
            if (cache != null) {
                String cacheKey = buildCacheKey(transformed);
                cache.put(cacheKey, landmarks);
                logger.debug("Cache populated for key: {}", cacheKey);
            }
        } catch (Exception e) {
            logger.warn("Failed to populate landmarks cache, continuing without cache: {}", e.getMessage());
        }
    }

    /**
     * Build cache key for landmarks and coordinate requests (geohash-style).
     */
    private String buildCacheKey(TransformedCoordinates transformed) {
        return transformed.getLat().toString() + ":" + transformed.getLng().toString() + ":" + queryRadiusMeters;
    }

    /**
     * Get CoordinateRequest from cache.
     * Gracefully handles cache unavailability (e.g., Redis connection failures).
     */
    private Optional<CoordinateRequest> getCoordinateRequestFromCache(TransformedCoordinates transformed) {
        try {
            Cache cache = cacheManager.getCache("coordinateRequests");
            if (cache != null) {
                String cacheKey = buildCacheKey(transformed);
                Cache.ValueWrapper wrapper = cache.get(cacheKey);
                if (wrapper != null) {
                    Object cachedValue = wrapper.get();
                    if (cachedValue instanceof CoordinateRequest) {
                        logger.debug("CoordinateRequest cache hit for key: {}", cacheKey);
                        return Optional.of((CoordinateRequest) cachedValue);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to get CoordinateRequest from cache, continuing without cache: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Put CoordinateRequest into cache.
     * Gracefully handles cache unavailability (e.g., Redis connection failures).
     */
    private void putCoordinateRequestInCache(TransformedCoordinates transformed, CoordinateRequest request) {
        try {
            Cache cache = cacheManager.getCache("coordinateRequests");
            if (cache != null) {
                String cacheKey = buildCacheKey(transformed);
                cache.put(cacheKey, request);
                logger.debug("CoordinateRequest cache populated for key: {}", cacheKey);
            }
        } catch (Exception e) {
            logger.warn("Failed to put CoordinateRequest into cache, continuing without cache: {}", e.getMessage());
        }
    }

    private LandmarksQueryResponseDto buildResponse(
            TransformedCoordinates transformed,
            List<LandmarkResponseDto> landmarks,
            String source) {
        LandmarksQueryResponseDto.KeyDto key = new LandmarksQueryResponseDto.KeyDto(
                transformed.getLat(),
                transformed.getLng(),
                queryRadiusMeters);
        return new LandmarksQueryResponseDto(key, source, landmarks);
    }
}
