package com.overpass.landmarks.application.port.out;

import com.overpass.landmarks.domain.model.Landmark;

import java.util.List;
import java.util.UUID;

/**
 * Output port for landmark persistence.
 */
public interface LandmarkRepository {

  /**
   * Find all landmarks for a given coordinate request.
   */
  List<Landmark> findByCoordinateRequestId(UUID coordinateRequestId);

  /**
   * Find landmarks by coordinate request key (for cache miss lookups).
   */
  List<Landmark> findByCoordinateRequestKey(
      java.math.BigDecimal keyLat,
      java.math.BigDecimal keyLng,
      Integer radiusMeters);

  /**
   * Save a landmark.
   */
  Landmark save(Landmark landmark);

  /**
   * Soft delete a landmark (sets deletedAt timestamp).
   */
  default void softDelete(Landmark landmark) {
    landmark.setDeletedAt(java.time.OffsetDateTime.now());
    save(landmark);
  }
}
