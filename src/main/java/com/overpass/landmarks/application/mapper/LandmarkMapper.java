package com.overpass.landmarks.application.mapper;

import com.overpass.landmarks.api.dto.LandmarkResponseDto;
import com.overpass.landmarks.domain.model.Landmark;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting between domain models and DTOs.
 */
@Component
public class LandmarkMapper {

  /**
   * Maps a domain Landmark to a LandmarkResponseDto.
   * 
   * @param landmark Domain model
   * @return DTO representation
   */
  public LandmarkResponseDto toDto(Landmark landmark) {
    return new LandmarkResponseDto(
        landmark.getId(),
        landmark.getName(),
        landmark.getOsmType().name(),
        landmark.getOsmId(),
        landmark.getLat(),
        landmark.getLng(),
        landmark.getTags());
  }
}
