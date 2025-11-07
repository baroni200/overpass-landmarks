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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
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
 * Processing Mode: Asynchronous
 * - POST /webhook returns immediately with request ID
 * - Processing happens asynchronously in background
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
  private final int queryRadiusMeters;
  private final Duration cacheExpirationDuration;

  public WebhookService(
      CoordinateTransformer coordinateTransformer,
      OverpassClient overpassClient,
      CoordinateRequestRepository coordinateRequestRepository,
      LandmarkRepository landmarkRepository,
      LandmarkMapper landmarkMapper,
      CacheManager cacheManager,
      @Value("${app.overpass.query-radius-meters:500}") int queryRadiusMeters,
      @Value("${app.overpass.cache-expiration-days:60}") int cacheExpirationDays) {
    this.coordinateTransformer = coordinateTransformer;
    this.overpassClient = overpassClient;
    this.coordinateRequestRepository = coordinateRequestRepository;
    this.landmarkRepository = landmarkRepository;
    this.landmarkMapper = landmarkMapper;
    this.cacheManager = cacheManager;
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
      logger.debug("Cache hit for key: {}, checking cache for CoordinateRequest", cacheKey);

      // Cache exists, check CoordinateRequest cache first
      Optional<CoordinateRequest> existingRequest = getCoordinateRequestFromCache(transformed);
      
      if (existingRequest.isEmpty()) {
        // CoordinateRequest cache miss - query DB
        existingRequest = coordinateRequestRepository
            .findByKeyLatAndKeyLngAndRadiusMeters(
                transformed.getLat(),
                transformed.getLng(),
                queryRadiusMeters);
        
        // Populate CoordinateRequest cache
        if (existingRequest.isPresent()) {
          putCoordinateRequestInCache(transformed, existingRequest.get());
        }
      }

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
          // Clear caches and continue to refresh
          evictFromCache(cacheKey);
          evictCoordinateRequestFromCache(cacheKey);
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

    // Step 3: Cache miss or expired - check CoordinateRequest cache first
    Optional<CoordinateRequest> existingRequest = getCoordinateRequestFromCache(transformed);
    
    if (existingRequest.isEmpty()) {
      // CoordinateRequest cache miss - query DB
      existingRequest = coordinateRequestRepository
          .findByKeyLatAndKeyLngAndRadiusMeters(
              transformed.getLat(),
              transformed.getLng(),
              queryRadiusMeters);
      
      // Populate CoordinateRequest cache
      if (existingRequest.isPresent()) {
        putCoordinateRequestInCache(transformed, existingRequest.get());
      }
    }

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
        // Evict CoordinateRequest cache
        evictCoordinateRequestFromCache(cacheKey);
      } else {
        // Return existing ID (request is completed)
        logger.info("Found existing completed request for key: {}:{}, returning existing ID: {}",
            transformed.getLat(), transformed.getLng(), request.getId());
        return new WebhookSubmissionResponseDto(request.getId(), request.getStatus().name());
      }
    }

    // Step 4: Create pending request
    CoordinateRequest pendingRequest = new CoordinateRequest(
        transformed.getLat(),
        transformed.getLng(),
        queryRadiusMeters);
    pendingRequest.setStatus(RequestStatus.PENDING);
    pendingRequest = coordinateRequestRepository.save(pendingRequest);
    
    // Populate CoordinateRequest cache
    putCoordinateRequestInCache(transformed, pendingRequest);

    logger.info("Created pending request with ID: {} for coordinates: {}",
        pendingRequest.getId(), transformed);

    // Step 5: Process asynchronously
    processWebhookAsync(pendingRequest.getId(), lat, lng);

    return new WebhookSubmissionResponseDto(pendingRequest.getId(), RequestStatus.PENDING.name());
  }

  /**
   * Process webhook request asynchronously: transform coordinates, query
   * Overpass, persist, and cache.
   * 
   * @param requestId The ID of the pending request to process
   * @param lat       Original latitude
   * @param lng       Original longitude
   */
  @Async
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
          
          // Update CoordinateRequest cache
          putCoordinateRequestInCache(transformed, coordinateRequest);

          // Cache already populated, skip Overpass API call
          logger.info("Completed async processing for request ID: {}, status: {} (from cache)",
              requestId, coordinateRequest.getStatus());
          return;
        }
      }

      // Step 4: Cache miss - Check CoordinateRequest cache first
      Optional<CoordinateRequest> existingDbRequest = getCoordinateRequestFromCache(transformed);
      
      if (existingDbRequest.isEmpty()) {
        // CoordinateRequest cache miss - query DB
        existingDbRequest = coordinateRequestRepository
            .findByKeyLatAndKeyLngAndRadiusMeters(
                transformed.getLat(),
                transformed.getLng(),
                queryRadiusMeters);
        
        // Populate CoordinateRequest cache
        if (existingDbRequest.isPresent()) {
          putCoordinateRequestInCache(transformed, existingDbRequest.get());
        }
      }

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
            
            // Update CoordinateRequest cache
            putCoordinateRequestInCache(transformed, coordinateRequest);

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
        
        // Populate CoordinateRequest cache
        putCoordinateRequestInCache(transformed, coordinateRequest);
        
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
   * Check if a webhook request exists and return its status.
   * 
   * @param requestId The request ID
   * @return RequestStatus if found, empty if not found
   */
  @Transactional(readOnly = true)
  public Optional<RequestStatus> getRequestStatus(UUID requestId) {
    Optional<CoordinateRequest> requestOpt = coordinateRequestRepository.findByIdNotDeleted(requestId);
    return requestOpt.map(CoordinateRequest::getStatus);
  }

  /**
   * Get webhook result by coordinates.
   * Finds or creates a CoordinateRequest based on transformed coordinates.
   * 
   * Caching Strategy: Cache-first → DB fallback → populate cache
   * 
   * @param lat Latitude
   * @param lng Longitude
   * @return Webhook response with landmarks
   */
  @Transactional(readOnly = true)
  public WebhookResponseDto getWebhookByCoordinates(BigDecimal lat, BigDecimal lng) {
    // Step 1: Transform coordinates
    Coordinates coordinates = new Coordinates(lat, lng);
    TransformedCoordinates transformed = coordinateTransformer.transform(coordinates);

    logger.info("Querying webhook by coordinates: {} -> {}", coordinates, transformed);

    // Step 2: Check cache first
    String cacheKey = buildCacheKey(transformed);
    Optional<List<LandmarkResponseDto>> cachedLandmarks = getFromCache(cacheKey);

    if (cachedLandmarks.isPresent()) {
      logger.debug("Cache hit for key: {} in getWebhookByCoordinates", cacheKey);
      WebhookResponseDto response = buildResponse(transformed, cachedLandmarks.get().size(),
          queryRadiusMeters, cachedLandmarks.get());
      return response;
    }

    // Step 3: Cache miss - check CoordinateRequest cache first
    Optional<CoordinateRequest> requestOpt = getCoordinateRequestFromCache(transformed);
    
    if (requestOpt.isEmpty()) {
      // CoordinateRequest cache miss - query DB
      requestOpt = coordinateRequestRepository
          .findByKeyLatAndKeyLngAndRadiusMeters(
              transformed.getLat(),
              transformed.getLng(),
              queryRadiusMeters);
      
      // Populate CoordinateRequest cache
      if (requestOpt.isPresent()) {
        putCoordinateRequestInCache(transformed, requestOpt.get());
      }
    }

    List<Landmark> landmarks;
    if (requestOpt.isPresent()) {
      CoordinateRequest request = requestOpt.get();
      // Load landmarks from database
      landmarks = landmarkRepository.findByCoordinateRequestId(request.getId());
      logger.debug("Found existing CoordinateRequest for key: {}:{}, loaded {} landmarks",
          transformed.getLat(), transformed.getLng(), landmarks.size());
    } else {
      // No CoordinateRequest exists yet - return empty response
      logger.debug("No CoordinateRequest found for key: {}:{}, returning empty response",
          transformed.getLat(), transformed.getLng());
      landmarks = List.of();
    }

    // Step 4: Map to DTOs
    List<LandmarkResponseDto> landmarkDtos = landmarks.stream()
        .map(landmarkMapper::toDto)
        .toList();

    // Step 5: Populate cache for next time
    if (!landmarks.isEmpty()) {
      populateCache(transformed, landmarks, queryRadiusMeters);
    }

    WebhookResponseDto response = buildResponse(transformed, landmarks.size(),
        queryRadiusMeters, landmarkDtos);

    return response;
  }

  /**
   * Get webhook result by request ID.
   * 
   * Caching Strategy: Cache-first → DB fallback → populate cache
   * 
   * @param requestId The request ID
   * @return Webhook response if completed, empty if pending or not found
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

    // Step 2: Check CoordinateRequest cache first
    Optional<CoordinateRequest> existingRequest = getCoordinateRequestFromCache(transformed);
    
    if (existingRequest.isEmpty()) {
      // CoordinateRequest cache miss - query DB
      existingRequest = coordinateRequestRepository
          .findByKeyLatAndKeyLngAndRadiusMeters(
              transformed.getLat(),
              transformed.getLng(),
              queryRadiusMeters);
      
      // Populate CoordinateRequest cache
      if (existingRequest.isPresent()) {
        putCoordinateRequestInCache(transformed, existingRequest.get());
      }
    }

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
        // Evict CoordinateRequest cache
        evictCoordinateRequestFromCache(buildCacheKey(transformed));
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
      
      // Populate CoordinateRequest cache
      putCoordinateRequestInCache(transformed, coordinateRequest);
      
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
      
      // Populate CoordinateRequest cache (even for errors, to avoid repeated API calls)
      putCoordinateRequestInCache(transformed, coordinateRequest);

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

  /**
   * Evict CoordinateRequest from cache.
   * Gracefully handles cache unavailability (e.g., Redis connection failures).
   */
  private void evictCoordinateRequestFromCache(String cacheKey) {
    try {
      Cache cache = cacheManager.getCache("coordinateRequests");
      if (cache != null) {
        cache.evict(cacheKey);
        logger.debug("Evicted CoordinateRequest cache entry for key: {}", cacheKey);
      }
    } catch (Exception e) {
      logger.warn("Failed to evict CoordinateRequest from cache: {}", e.getMessage());
    }
  }

  /**
   * Get landmarks from cache.
   * Gracefully handles cache unavailability (e.g., Redis connection failures).
   */
  private Optional<List<LandmarkResponseDto>> getFromCache(String cacheKey) {
    try {
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
    } catch (Exception e) {
      logger.warn("Failed to get landmarks from cache, continuing without cache: {}", e.getMessage());
    }
    return Optional.empty();
  }

  /**
   * Evict entry from cache.
   * Gracefully handles cache unavailability (e.g., Redis connection failures).
   */
  private void evictFromCache(String cacheKey) {
    try {
      Cache cache = cacheManager.getCache("landmarks");
      if (cache != null) {
        cache.evict(cacheKey);
        logger.debug("Evicted cache entry for key: {}", cacheKey);
      }
    } catch (Exception e) {
      logger.warn("Failed to evict landmarks from cache: {}", e.getMessage());
    }
  }

  /**
   * Populate cache with landmarks (write-through strategy).
   * Gracefully handles cache unavailability (e.g., Redis connection failures).
   */
  private void populateCache(TransformedCoordinates transformed, List<Landmark> landmarks,
      int queryRadiusMeters) {
    try {
      List<LandmarkResponseDto> landmarkDtos = landmarks.stream()
          .map(landmarkMapper::toDto)
          .toList();
      
      Cache cache = cacheManager.getCache("landmarks");
      if (cache != null && !landmarkDtos.isEmpty()) {
        String cacheKey = buildCacheKey(transformed);
        cache.put(cacheKey, landmarkDtos);
        logger.debug("Cache populated for key: {}", cacheKey);
      }
    } catch (Exception e) {
      logger.warn("Failed to populate landmarks cache, continuing without cache: {}", e.getMessage());
    }
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
