package com.overpass.landmarks.api.controller;

import com.overpass.landmarks.api.dto.CoordinatesDto;
import com.overpass.landmarks.api.dto.LandmarksQueryResponseDto;
import com.overpass.landmarks.application.port.in.QueryLandmarksUseCase;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for landmarks query endpoint.
 * Retrieves landmarks using cache-first strategy with database fallback.
 */
@RestController
@RequestMapping("/landmarks")
@Validated
public class LandmarkController {

    private static final Logger logger = LoggerFactory.getLogger(LandmarkController.class);

    private final QueryLandmarksUseCase queryLandmarksUseCase;

    public LandmarkController(QueryLandmarksUseCase queryLandmarksUseCase) {
        this.queryLandmarksUseCase = queryLandmarksUseCase;
    }

    /**
     * GET /landmarks?lat=X&lng=Y
     * 
     * Query landmarks by coordinates.
     * Strategy: Cache-first → DB fallback → populate cache
     * 
     * @param coordinates Coordinate DTO with validated lat/lng
     * @return Response with landmarks and source indicator (cache/db/none)
     */
    @GetMapping
    public ResponseEntity<LandmarksQueryResponseDto> getLandmarks(
            @Valid @ModelAttribute CoordinatesDto coordinates) {
        logger.info("Querying landmarks: lat={}, lng={}", coordinates.getLat(), coordinates.getLng());

        LandmarksQueryResponseDto response = queryLandmarksUseCase.queryLandmarks(
                coordinates.getLat(),
                coordinates.getLng());
        return ResponseEntity.ok(response);
    }
}

