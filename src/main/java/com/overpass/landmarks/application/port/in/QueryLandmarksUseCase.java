package com.overpass.landmarks.application.port.in;

import com.overpass.landmarks.api.dto.LandmarksQueryResponseDto;

import java.math.BigDecimal;

/**
 * Input port for querying landmarks.
 * Defines the use case interface for the landmark query service.
 */
public interface QueryLandmarksUseCase {

  /**
   * Query landmarks by coordinates.
   * Strategy: Cache-first → DB fallback → populate cache
   * 
   * @param lat Latitude
   * @param lng Longitude
   * @return Query response with landmarks and source indicator
   */
  LandmarksQueryResponseDto queryLandmarks(BigDecimal lat, BigDecimal lng);
}
