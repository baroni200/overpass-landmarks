package com.overpass.landmarks.domain.service;

import com.overpass.landmarks.domain.model.Coordinates;
import com.overpass.landmarks.domain.model.TransformedCoordinates;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Domain service for transforming coordinates.
 * 
 * Transformation Rule: Round to 4 decimal places (~11m precision)
 * 
 * Rationale:
 * - Provides ~11 meter precision, which is suitable for landmark queries
 * - Deterministic and consistent for idempotency
 * - Simple to implement and understand
 * - Ensures nearby coordinates map to the same cache key
 */
@Service
public class CoordinateTransformer {

    private static final int DECIMAL_PLACES = 4;

    /**
     * Transforms coordinates by rounding to 4 decimal places.
     * This creates a stable key for caching and idempotency.
     * 
     * @param coordinates Original coordinates
     * @return Transformed coordinates rounded to 4 decimal places
     */
    public TransformedCoordinates transform(Coordinates coordinates) {
        BigDecimal transformedLat = coordinates.getLat()
            .setScale(DECIMAL_PLACES, RoundingMode.HALF_UP);
        BigDecimal transformedLng = coordinates.getLng()
            .setScale(DECIMAL_PLACES, RoundingMode.HALF_UP);
        
        return new TransformedCoordinates(transformedLat, transformedLng);
    }

    /**
     * Transforms raw latitude and longitude values.
     * 
     * @param lat Original latitude
     * @param lng Original longitude
     * @return Transformed coordinates rounded to 4 decimal places
     */
    public TransformedCoordinates transform(BigDecimal lat, BigDecimal lng) {
        return transform(new Coordinates(lat, lng));
    }
}

