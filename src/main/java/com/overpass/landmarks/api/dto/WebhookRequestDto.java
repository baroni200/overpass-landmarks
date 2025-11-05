package com.overpass.landmarks.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;

/**
 * DTO for webhook request body.
 * Extends CoordinatesDto to reuse coordinate validation.
 * Uses @JsonProperty to map JSON field names.
 */
@EqualsAndHashCode(callSuper = true)
public class WebhookRequestDto extends CoordinatesDto {

    public WebhookRequestDto() {
        super();
    }

    public WebhookRequestDto(java.math.BigDecimal lat, java.math.BigDecimal lng) {
        super(lat, lng);
    }

    @JsonProperty("lat")
    @Override
    public java.math.BigDecimal getLat() {
        return super.getLat();
    }

    @JsonProperty("lat")
    @Override
    public void setLat(java.math.BigDecimal lat) {
        super.setLat(lat);
    }

    @JsonProperty("lng")
    @Override
    public java.math.BigDecimal getLng() {
        return super.getLng();
    }

    @JsonProperty("lng")
    @Override
    public void setLng(java.math.BigDecimal lng) {
        super.setLng(lng);
    }
}

