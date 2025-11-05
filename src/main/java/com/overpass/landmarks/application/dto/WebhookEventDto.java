package com.overpass.landmarks.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO for webhook events sent to Kafka.
 * Contains the coordinate information needed to process the webhook
 * asynchronously.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEventDto {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("lat")
    private BigDecimal lat;

    @JsonProperty("lng")
    private BigDecimal lng;

    @JsonProperty("timestamp")
    private OffsetDateTime timestamp;

    public WebhookEventDto(BigDecimal lat, BigDecimal lng) {
        this.id = UUID.randomUUID();
        this.lat = lat;
        this.lng = lng;
        this.timestamp = OffsetDateTime.now();
    }
}
