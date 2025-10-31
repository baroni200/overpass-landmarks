package com.overpass.landmarks.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WebhookResponseDto {

    @JsonProperty("key")
    private KeyDto key;

    @JsonProperty("count")
    private Integer count;

    @JsonProperty("radiusMeters")
    private Integer radiusMeters;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeyDto {
        @JsonProperty("lat")
        private BigDecimal lat;

        @JsonProperty("lng")
        private BigDecimal lng;
    }
}

