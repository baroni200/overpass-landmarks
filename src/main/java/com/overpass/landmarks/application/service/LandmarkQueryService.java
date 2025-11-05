package com.overpass.landmarks.application.service;

import com.overpass.landmarks.api.dto.LandmarkResponseDto;
import com.overpass.landmarks.api.dto.LandmarksQueryResponseDto;
import com.overpass.landmarks.application.mapper.LandmarkMapper;
import com.overpass.landmarks.application.port.in.QueryLandmarksUseCase;
import com.overpass.landmarks.application.port.out.CoordinateRequestRepository;
import com.overpass.landmarks.application.port.out.LandmarkRepository;
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
                    List<LandmarkResponseDto> cachedLandmarks = (List<LandmarkResponseDto>) cachedList;
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
                        queryRadiusMeters);

        if (request.isEmpty()) {
            logger.debug("No data found in database for key: {}:{}", transformed.getLat(), transformed.getLng());
            return buildResponse(transformed, List.of(), "none");
        }

        // Step 4: Load landmarks from database
        List<Landmark> landmarks = landmarkRepository.findByCoordinateRequestId(request.get().getId());
        List<LandmarkResponseDto> landmarkDtos = landmarks.stream()
                .map(landmarkMapper::toDto)
                .toList();

        // Step 5: Populate cache for next time
        populateCache(transformed, landmarkDtos, queryRadiusMeters);

        return buildResponse(transformed, landmarkDtos, "db");
    }

    /**
     * Populate cache (write-through on GET).
     */
    private void populateCache(TransformedCoordinates transformed, List<LandmarkResponseDto> landmarks,
            int queryRadiusMeters) {
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
            String source) {
        LandmarksQueryResponseDto.KeyDto key = new LandmarksQueryResponseDto.KeyDto(
                transformed.getLat(),
                transformed.getLng(),
                queryRadiusMeters);
        return new LandmarksQueryResponseDto(key, source, landmarks);
    }
}
