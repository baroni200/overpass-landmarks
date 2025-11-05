package com.overpass.landmarks.application.port.out;

import com.overpass.landmarks.domain.model.CoordinateRequest;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Output port for coordinate request persistence.
 * Used for idempotency checks and cache miss lookups.
 */
public interface CoordinateRequestRepository {

  /**
   * Find coordinate request by transformed key and radius.
   * Used for idempotency checks and cache miss lookups.
   */
  Optional<CoordinateRequest> findByKeyLatAndKeyLngAndRadiusMeters(
      BigDecimal keyLat,
      BigDecimal keyLng,
      Integer radiusMeters);

  /**
   * Check if a coordinate request exists for the given key.
   */
  boolean existsByKeyLatAndKeyLngAndRadiusMeters(
      BigDecimal keyLat,
      BigDecimal keyLng,
      Integer radiusMeters);

  /**
   * Save a coordinate request.
   */
  CoordinateRequest save(CoordinateRequest coordinateRequest);

  /**
   * Count all coordinate requests.
   */
  long count();

  /**
   * Soft delete a coordinate request (sets deletedAt timestamp).
   */
  default void softDelete(CoordinateRequest coordinateRequest) {
    coordinateRequest.setDeletedAt(java.time.OffsetDateTime.now());
    save(coordinateRequest);
  }
}
