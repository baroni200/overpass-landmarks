package com.overpass.landmarks.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * DTO for webhook acknowledgment response.
 * Returned immediately when a webhook request is queued for async processing.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WebhookAcknowledgmentDto {

    @JsonProperty("message")
    private String message;

    @JsonProperty("eventId")
    private UUID eventId;

    @JsonProperty("status")
    private String status;

    public WebhookAcknowledgmentDto(UUID eventId) {
        this.message = "Webhook request queued for processing";
        this.eventId = eventId;
        this.status = "ACCEPTED";
    }
}
