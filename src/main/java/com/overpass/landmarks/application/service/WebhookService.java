package com.overpass.landmarks.application.service;

import com.overpass.landmarks.api.dto.LandmarkResponseDto;
import com.overpass.landmarks.api.dto.WebhookResponseDto;
import com.overpass.landmarks.application.mapper.LandmarkMapper;
import com.overpass.landmarks.application.port.in.ProcessWebhookUseCase;
import com.overpass.landmarks.application.port.out.CoordinateRequestRepository;
import com.overpass.landmarks.application.port.out.LandmarkRepository;
import com.overpass.landmarks.domain.model.*;
import com.overpass.landmarks.domain.policy.CoordinateTransformer;
import com.overpass.landmarks.infrastructure.http.OverpassClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Application service orchestrating webhook processing.
 * 
 * Processing Mode: Synchronous
 * Rationale:
 * - Simpler implementation for coding challenge
 * - Immediate feedback to webhook sender
 * - Overpass API typically responds within seconds
 * - Can be upgraded to async if needed for production
 */
@Service
public class WebhookService implements ProcessWebhookUseCase {

  private static final Logger logger = LoggerFactory.getLogger(WebhookService.class);

  private final CoordinateTransformer coordinateTransformer;
  private final OverpassClient overpassClient;
  private final CoordinateRequestRepository coordinateRequestRepository;
  private final LandmarkRepository landmarkRepository;
  private final LandmarkMapper landmarkMapper;
  private final int queryRadiusMeters;
  private final Duration cacheExpirationDuration;

  public WebhookService(
      CoordinateTransformer coordinateTransformer,
      OverpassClient overpassClient,
      CoordinateRequestRepository coordinateRequestRepository,
      LandmarkRepository landmarkRepository,
      LandmarkMapper landmarkMapper,
      @Value("${app.overpass.query-radius-meters:500}") int queryRadiusMeters,
      @Value("${app.overpass.cache-expiration-days:60}") int cacheExpirationDays) {
    this.coordinateTransformer = coordinateTransformer;
    this.overpassClient = overpassClient;
    this.coordinateRequestRepository = coordinateRequestRepository;
    this.landmarkRepository = landmarkRepository;
    this.landmarkMapper = landmarkMapper;
    this.queryRadiusMeters = queryRadiusMeters;
    this.cacheExpirationDuration = Duration.ofDays(cacheExpirationDays);
  }

  /**
   * Process webhook request: transform coordinates, query Overpass, persist, and
   * cache.
   * 
   * Idempotency: If a request with the same transformed key already exists,
   * returns the existing result without querying Overpass again.
   * 
   * Cache Expiration: If the existing request is older than the configured
   * expiration
   * (default: 60 days), the data is refreshed by querying Overpass API again.
   */
  @Override
  @Transactional
  public WebhookResponseDto processWebhook(BigDecimal lat, BigDecimal lng) {
    // Step 1: Transform coordinates
    Coordinates coordinates = new Coordinates(lat, lng);
    TransformedCoordinates transformed = coordinateTransformer.transform(coordinates);

    logger.info("Processing webhook for coordinates: {} -> {}", coordinates, transformed);

    // Step 2: Check for existing request (idempotency with expiration check)
    Optional<CoordinateRequest> existingRequest = coordinateRequestRepository
        .findByKeyLatAndKeyLngAndRadiusMeters(
            transformed.getLat(),
            transformed.getLng(),
            queryRadiusMeters);

    if (existingRequest.isPresent()) {
      CoordinateRequest request = existingRequest.get();

      // Check if request is expired (older than expiration duration)
      OffsetDateTime expirationTime = request.getRequestedAt().plus(cacheExpirationDuration);
      boolean isExpired = OffsetDateTime.now().isAfter(expirationTime);

      if (!isExpired) {
        logger.info("Found existing request for key: {}:{}, returning cached result (age: {} days)",
            transformed.getLat(), transformed.getLng(),
            Duration.between(request.getRequestedAt(), OffsetDateTime.now()).toDays());
        List<Landmark> landmarks = landmarkRepository.findByCoordinateRequestId(request.getId());

        // Populate cache
        if (landmarks.size() > 0) {
          populateCache(transformed, landmarks, queryRadiusMeters);

          List<LandmarkResponseDto> landmarkDtos = landmarks.stream()
              .map(landmarkMapper::toDto)
              .toList();
          return buildResponse(transformed, landmarks.size(), queryRadiusMeters, landmarkDtos);
        }
      } else {
        logger.info(
            "Found existing request for key: {}:{}, but expired (age: {} days). Refreshing data from Overpass API",
            transformed.getLat(), transformed.getLng(),
            Duration.between(request.getRequestedAt(), OffsetDateTime.now()).toDays());
        // Soft delete old request and landmarks to refresh
        List<Landmark> oldLandmarks = landmarkRepository.findByCoordinateRequestId(request.getId());
        oldLandmarks.forEach(landmark -> landmarkRepository.softDelete(landmark));
        coordinateRequestRepository.softDelete(request);
      }
    }

    // Step 3: Query Overpass API
    CoordinateRequest coordinateRequest;
    List<Landmark> landmarks;

    try {
      List<OverpassClient.OverpassLandmark> overpassLandmarks = overpassClient
          .queryLandmarks(transformed.getLat(), transformed.getLng(), queryRadiusMeters);

      // Step 4: Persist coordinate request
      CoordinateRequest newRequest = new CoordinateRequest(
          transformed.getLat(),
          transformed.getLng(),
          queryRadiusMeters);

      if (overpassLandmarks.isEmpty()) {
        newRequest.setStatus(RequestStatus.EMPTY);
        logger.info("No landmarks found for coordinates: {}", transformed);
      } else {
        newRequest.setStatus(RequestStatus.FOUND);
        logger.info("Found {} landmarks for coordinates: {}", overpassLandmarks.size(), transformed);
      }

      coordinateRequest = coordinateRequestRepository.save(newRequest);
      final CoordinateRequest finalCoordinateRequest = coordinateRequest;

      // Step 5: Persist landmarks
      landmarks = overpassLandmarks.stream()
          .map(overpassLandmark -> {
            Landmark landmark = new Landmark(
                finalCoordinateRequest,
                overpassLandmark.getOsmType(),
                overpassLandmark.getOsmId(),
                overpassLandmark.getLat(),
                overpassLandmark.getLng());
            landmark.setName(overpassLandmark.getName());
            landmark.setTags(overpassLandmark.getTags());
            return landmark;
          })
          .map(landmarkRepository::save)
          .toList();

    } catch (OverpassClient.OverpassException e) {
      logger.error("Overpass API error for coordinates: {}", transformed, e);

      // Record error in database
      CoordinateRequest errorRequest = new CoordinateRequest(
          transformed.getLat(),
          transformed.getLng(),
          queryRadiusMeters);
      errorRequest.setStatus(RequestStatus.ERROR);
      errorRequest.setErrorMessage(e.getMessage());
      coordinateRequest = coordinateRequestRepository.save(errorRequest);

      throw new WebhookProcessingException("Failed to query Overpass API", e);
    }

    // Step 6: Write-through cache
    populateCache(transformed, landmarks, queryRadiusMeters);

    List<LandmarkResponseDto> landmarkDtos = landmarks.stream()
        .map(landmarkMapper::toDto)
        .toList();
    return buildResponse(transformed, landmarks.size(), queryRadiusMeters, landmarkDtos);
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

  private WebhookResponseDto buildResponse(TransformedCoordinates transformed, int count, int radiusMeters,
      List<LandmarkResponseDto> landmarks) {
    WebhookResponseDto.KeyDto key = new WebhookResponseDto.KeyDto(
        transformed.getLat(),
        transformed.getLng());
    return new WebhookResponseDto(key, count, radiusMeters, landmarks);
  }

  /**
   * Exception thrown when webhook processing fails.
   */
  public static class WebhookProcessingException extends RuntimeException {
    public WebhookProcessingException(String message) {
      super(message);
    }

    public WebhookProcessingException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
