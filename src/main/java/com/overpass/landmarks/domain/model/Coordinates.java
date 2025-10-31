package com.overpass.landmarks.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * Value object representing a geographic coordinate pair.
 */
@Getter
@EqualsAndHashCode
@ToString
public class Coordinates {
    private final BigDecimal lat;
    private final BigDecimal lng;

    public Coordinates(BigDecimal lat, BigDecimal lng) {
        if (lat == null || lng == null) {
            throw new IllegalArgumentException("Latitude and longitude must not be null");
        }
        if (lat.compareTo(new BigDecimal("-90")) < 0 || lat.compareTo(new BigDecimal("90")) > 0) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90");
        }
        if (lng.compareTo(new BigDecimal("-180")) < 0 || lng.compareTo(new BigDecimal("180")) > 0) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180");
        }
        this.lat = lat;
        this.lng = lng;
    }
}

