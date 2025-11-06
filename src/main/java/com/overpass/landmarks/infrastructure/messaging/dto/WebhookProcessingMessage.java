package com.overpass.landmarks.infrastructure.messaging.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Kafka message DTO for webhook processing.
 * Contains all information needed to process a webhook request asynchronously.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WebhookProcessingMessage {

    @JsonProperty("requestId")
    private UUID requestId;

    @JsonProperty("lat")
    private BigDecimal lat;

    @JsonProperty("lng")
    private BigDecimal lng;
}
