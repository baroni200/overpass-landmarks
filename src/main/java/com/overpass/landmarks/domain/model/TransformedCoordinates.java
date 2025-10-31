package com.overpass.landmarks.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * Value object representing transformed coordinates used as a cache key.
 */
@Getter
@EqualsAndHashCode
@ToString
public class TransformedCoordinates {
    private final BigDecimal lat;
    private final BigDecimal lng;

    public TransformedCoordinates(BigDecimal lat, BigDecimal lng) {
        if (lat == null || lng == null) {
            throw new IllegalArgumentException("Transformed latitude and longitude must not be null");
        }
        this.lat = lat;
        this.lng = lng;
    }
}

