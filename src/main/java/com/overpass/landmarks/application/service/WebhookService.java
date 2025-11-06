package com.overpass.landmarks.application.service;

import com.overpass.landmarks.api.dto.LandmarkResponseDto;
import com.overpass.landmarks.api.dto.WebhookResponseDto;
import com.overpass.landmarks.api.dto.WebhookSubmissionResponseDto;
import com.overpass.landmarks.application.mapper.LandmarkMapper;
import com.overpass.landmarks.application.port.in.ProcessWebhookUseCase;
import com.overpass.landmarks.application.port.out.CoordinateRequestRepository;
import com.overpass.landmarks.application.port.out.LandmarkRepository;
import com.overpass.landmarks.domain.model.*;
import com.overpass.landmarks.domain.policy.CoordinateTransformer;
import com.overpass.landmarks.infrastructure.http.OverpassClient;
import com.overpass.landmarks.infrastructure.messaging.producer.WebhookProcessingProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service orchestrating webhook processing.
 * 
 * Processing Mode: Asynchronous via Kafka
 * - POST /webhook returns immediately with request ID
 * - Processing happens asynchronously via Kafka queue
 * - GET /webhook/{id} retrieves results when ready
 */
@Service
public class WebhookService implements ProcessWebhookUseCase {

  private static final Logger logger = LoggerFactory.getLogger(WebhookService.class);

  private final CoordinateTransformer coordinateTransformer;
  private final OverpassClient overpassClient;
  private final CoordinateRequestRepository coordinateRequestRepository;
  private final LandmarkRepository landmarkRepository;
  private final LandmarkMapper landmarkMapper;
  private final CacheManager cacheManager;
  private final WebhookProcessingProducer webhookProcessingProducer;
  private final int queryRadiusMeters;
  private final Duration cacheExpirationDuration;

  public WebhookService(
      CoordinateTransformer coordinateTransformer,
      OverpassClient overpassClient,
      CoordinateRequestRepository coordinateRequestRepository,
      LandmarkRepository landmarkRepository,
      LandmarkMapper landmarkMapper,
      CacheManager cacheManager,
      WebhookProcessingProducer webhookProcessingProducer,
      @Value("${app.overpass.query-radius-meters:500}") int queryRadiusMeters,
      @Value("${app.overpass.cache-expiration-days:60}") int cacheExpirationDays) {
    this.coordinateTransformer = coordinateTransformer;
    this.overpassClient = overpassClient;
    this.coordinateRequestRepository = coordinateRequestRepository;
    this.landmarkRepository = landmarkRepository;
    this.landmarkMapper = landmarkMapper;
    this.cacheManager = cacheManager;
    this.webhookProcessingProducer = webhookProcessingProducer;
    this.queryRadiusMeters = queryRadiusMeters;
    this.cacheExpirationDuration = Duration.ofDays(cacheExpirationDays);
  }

  /**
   * Submit webhook request: creates a pending request and returns ID immediately.
   * Processing happens asynchronously.
   * 
   * Caching Strategy: Cache-first → DB check → Skip Overpass API if cache exists
   * 
   * @param lat Latitude
   * @param lng Longitude
   * @return Webhook submission response with request ID
   */
  @Transactional
  public WebhookSubmissionResponseDto submitWebhook(BigDecimal lat, BigDecimal lng) {
    // Step 1: Transform coordinates
    Coordinates coordinates = new Coordinates(lat, lng);
    TransformedCoordinates transformed = coordinateTransformer.transform(coordinates);

    logger.info("Submitting webhook for coordinates: {} -> {}", coordinates, transformed);

    // Step 2: Check cache first
    String cacheKey = buildCacheKey(transformed);
    Optional<List<LandmarkResponseDto>> cachedLandmarks = getFromCache(cacheKey);

    if (cachedLandmarks.isPresent()) {
      logger.debug("Cache hit for key: {}, checking DB for request ID", cacheKey);

      // Cache exists, check DB for coordinate_request to get ID
      Optional<CoordinateRequest> existingRequest = coordinateRequestRepository
          .findByKeyLatAndKeyLngAndRadiusMeters(
              transformed.getLat(),
              transformed.getLng(),
              queryRadiusMeters);

      if (existingRequest.isPresent()) {
        CoordinateRequest request = existingRequest.get();

        // Check if request is expired
        OffsetDateTime expirationTime = request.getRequestedAt().plus(cacheExpirationDuration);
        boolean isExpired = OffsetDateTime.now().isAfter(expirationTime);

        if (!isExpired && request.getStatus() != RequestStatus.PENDING) {
          logger.info("Cache hit for key: {}, returning existing request ID: {} (skipping Overpass API)",
              cacheKey, request.getId());
          return new WebhookSubmissionResponseDto(request.getId(), request.getStatus().name());
        }

        if (isExpired) {
          logger.info("Cache exists but request expired, will refresh");
          // Clear cache and continue to refresh
          evictFromCache(cacheKey);
          // Soft delete old request and landmarks to refresh
          List<Landmark> oldLandmarks = landmarkRepository.findByCoordinateRequestId(request.getId());
          oldLandmarks.forEach(landmark -> landmarkRepository.softDelete(landmark));
          coordinateRequestRepository.softDelete(request);
        } else if (request.getStatus() == RequestStatus.PENDING) {
          logger.info("Cache hit but request still pending, returning existing ID: {}", request.getId());
          return new WebhookSubmissionResponseDto(request.getId(), RequestStatus.PENDING.name());
        }
      }
    }

    // Step 3: Cache miss or expired - check DB for existing request
    Optional<CoordinateRequest> existingRequest = coordinateRequestRepository
        .findByKeyLatAndKeyLngAndRadiusMeters(
            transformed.getLat(),
            transformed.getLng(),
            queryRadiusMeters);

    if (existingRequest.isPresent()) {
      CoordinateRequest request = existingRequest.get();

      // If request is still pending, return existing ID
      if (request.getStatus() == RequestStatus.PENDING) {
        logger.info("Found existing pending request for key: {}:{}, returning existing ID: {}",
            transformed.getLat(), transformed.getLng(), request.getId());
        return new WebhookSubmissionResponseDto(request.getId(), RequestStatus.PENDING.name());
      }

      // Check if request is expired (older than expiration duration)
      OffsetDateTime expirationTime = request.getRequestedAt().plus(cacheExpirationDuration);
      boolean isExpired = OffsetDateTime.now().isAfter(expirationTime);

      if (isExpired) {
        logger.info(
            "Found existing request for key: {}:{}, but expired (age: {} days). Refreshing data from Overpass API",
            transformed.getLat(), transformed.getLng(),
            Duration.between(request.getRequestedAt(), OffsetDateTime.now()).toDays());
        // Soft delete old request and landmarks to refresh
        List<Landmark> oldLandmarks = landmarkRepository.findByCoordinateRequestId(request.getId());
        oldLandmarks.forEach(landmark -> landmarkRepository.softDelete(landmark));
        coordinateRequestRepository.softDelete(request);
      } else {
        // Return existing ID (request is completed)
        logger.info("Found existing completed request for key: {}:{}, returning existing ID: {}",
            transformed.getLat(), transformed.getLng(), request.getId());
        return new WebhookSubmissionResponseDto(request.getId(), request.getStatus().name());
      }
    }

    // Step 3: Create pending request
    CoordinateRequest pendingRequest = new CoordinateRequest(
        transformed.getLat(),
        transformed.getLng(),
        queryRadiusMeters);
    pendingRequest.setStatus(RequestStatus.PENDING);
    pendingRequest = coordinateRequestRepository.save(pendingRequest);

    logger.info("Created pending request with ID: {} for coordinates: {}",
        pendingRequest.getId(), transformed);

    // Step 5: Send to Kafka queue for async processing
    webhookProcessingProducer.sendWebhookProcessingMessage(
        pendingRequest.getId(), lat, lng);

    return new WebhookSubmissionResponseDto(pendingRequest.getId(), RequestStatus.PENDING.name());
  }

  /**
   * Process webhook request asynchronously: transform coordinates, query
   * Overpass, persist, and cache.
   * 
   * @deprecated This method is now handled by Kafka consumer
   *             (WebhookProcessingConsumer).
   *             Use Kafka producer to send messages instead.
   * 
   * @param requestId The ID of the pending request to process
   * @param lat       Original latitude
   * @param lng       Original longitude
   */
  @Deprecated
  @Transactional
  public void processWebhookAsync(UUID requestId, BigDecimal lat, BigDecimal lng) {
    try {
      logger.info("Starting async processing for request ID: {}", requestId);

      // Retrieve the pending request
      Optional<CoordinateRequest> requestOpt = coordinateRequestRepository.findByIdNotDeleted(requestId);
      if (requestOpt.isEmpty()) {
        logger.error("Request not found for ID: {}", requestId);
        return;
      }

      CoordinateRequest coordinateRequest = requestOpt.get();

      // Ensure it's still pending
      if (coordinateRequest.getStatus() != RequestStatus.PENDING) {
        logger.info("Request {} is already processed with status: {}",
            requestId, coordinateRequest.getStatus());
        return;
      }

      TransformedCoordinates transformed = new TransformedCoordinates(
          coordinateRequest.getKeyLat(),
          coordinateRequest.getKeyLng());

      // Step 3: Check cache first before querying Overpass API
      String cacheKey = buildCacheKey(transformed);
      Optional<List<LandmarkResponseDto>> cachedLandmarks = getFromCache(cacheKey);

      List<Landmark> landmarks;
      if (cachedLandmarks.isPresent()) {
        logger.info("Cache hit for key: {}, skipping Overpass API call for request ID: {}",
            cacheKey, requestId);

        // Cache found - load landmarks from DB (they should already exist)
        landmarks = landmarkRepository.findByCoordinateRequestId(requestId);

        if (landmarks.isEmpty()) {
          logger.warn("Cache hit but no landmarks in DB for request ID: {}, will query Overpass API", requestId);
          // Fall through to query Overpass API
        } else {
          // Update status based on landmarks
          if (landmarks.isEmpty()) {
            coordinateRequest.setStatus(RequestStatus.EMPTY);
          } else {
            coordinateRequest.setStatus(RequestStatus.FOUND);
          }
          coordinateRequest = coordinateRequestRepository.save(coordinateRequest);

          // Cache already populated, skip Overpass API call
          logger.info("Completed async processing for request ID: {}, status: {} (from cache)",
              requestId, coordinateRequest.getStatus());
          return;
        }
      }

      // Step 4: Cache miss - Check DB for existing landmarks before calling Overpass
      // API
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
          logger.info(
              "Cache miss but found valid data in DB for key: {}, loading from DB for request ID: {} (skipping Overpass API)",
              cacheKey, requestId);
          landmarks = landmarkRepository.findByCoordinateRequestId(dbRequest.getId());

          if (!landmarks.isEmpty()) {
            // Update current request status and load landmarks
            coordinateRequest.setStatus(RequestStatus.FOUND);
            coordinateRequest = coordinateRequestRepository.save(coordinateRequest);

            // Populate cache for next time
            populateCache(transformed, landmarks, queryRadiusMeters);

            logger.info("Completed async processing for request ID: {}, status: {} (from DB, skipping Overpass API)",
                requestId, coordinateRequest.getStatus());
            return;
          }
        }
      }

      // Step 5: Cache miss AND DB miss (or expired) - Query Overpass API
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

        // Step 6: Persist landmarks
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
        logger.error("Overpass API error for request ID: {}", requestId, e);
        coordinateRequest.setStatus(RequestStatus.ERROR);
        coordinateRequest.setErrorMessage(e.getMessage());
        coordinateRequestRepository.save(coordinateRequest);
        return;
      }

      // Step 7: Write-through cache
      populateCache(transformed, landmarks, queryRadiusMeters);

      logger.info("Completed async processing for request ID: {}, status: {}",
          requestId, coordinateRequest.getStatus());
    } catch (Exception e) {
      logger.error("Unexpected error processing webhook async for request ID: {}", requestId, e);
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
    }
  }

  /**
   * Get webhook result by request ID.
   * 
   * Caching Strategy: Cache-first → DB fallback → populate cache
   * 
   * @param requestId The request ID
   * @return Webhook response if completed, null if pending or not found
   */
  @Transactional(readOnly = true)
  public Optional<WebhookResponseDto> getWebhookStatus(UUID requestId) {
    Optional<CoordinateRequest> requestOpt = coordinateRequestRepository.findByIdNotDeleted(requestId);

    if (requestOpt.isEmpty()) {
      return Optional.empty();
    }

    CoordinateRequest request = requestOpt.get();

    // If still pending, return empty
    if (request.getStatus() == RequestStatus.PENDING) {
      return Optional.empty();
    }

    TransformedCoordinates transformed = new TransformedCoordinates(
        request.getKeyLat(),
        request.getKeyLng());

    // Step 1: Check cache first
    String cacheKey = buildCacheKey(transformed);
    Optional<List<LandmarkResponseDto>> cachedLandmarks = getFromCache(cacheKey);

    if (cachedLandmarks.isPresent()) {
      logger.debug("Cache hit for key: {} in getWebhookStatus, request ID: {}", cacheKey, requestId);
      WebhookResponseDto response = buildResponse(transformed, cachedLandmarks.get().size(),
          request.getRadiusMeters(), cachedLandmarks.get());
      return Optional.of(response);
    }

    // Step 2: Cache miss - load from database
    logger.debug("Cache miss for key: {} in getWebhookStatus, loading from DB, request ID: {}",
        cacheKey, requestId);
    List<Landmark> landmarks = landmarkRepository.findByCoordinateRequestId(request.getId());
    List<LandmarkResponseDto> landmarkDtos = landmarks.stream()
        .map(landmarkMapper::toDto)
        .toList();

    // Step 3: Populate cache for next time
    populateCache(transformed, landmarks, request.getRadiusMeters());

    WebhookResponseDto response = buildResponse(transformed, landmarks.size(),
        request.getRadiusMeters(), landmarkDtos);

    return Optional.of(response);
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
   * 
   * @deprecated Use submitWebhook() and getWebhookStatus() for async processing
   */
  @Deprecated
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
   * Evict entry from cache.
   */
  private void evictFromCache(String cacheKey) {
    Cache cache = cacheManager.getCache("landmarks");
    if (cache != null) {
      cache.evict(cacheKey);
      logger.debug("Evicted cache entry for key: {}", cacheKey);
    }
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
