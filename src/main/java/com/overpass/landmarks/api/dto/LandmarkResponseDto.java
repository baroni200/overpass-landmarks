package com.overpass.landmarks.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LandmarkResponseDto {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("osmType")
    private String osmType;

    @JsonProperty("osmId")
    private Long osmId;

    @JsonProperty("lat")
    private BigDecimal lat;

    @JsonProperty("lng")
    private BigDecimal lng;

    @JsonProperty("tags")
    private Map<String, Object> tags;
}

