package com.overpass.landmarks.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * DTO for webhook submission response.
 * Returns immediately with the request ID for async processing.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WebhookSubmissionResponseDto {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("status")
    private String status = "PENDING";
}
