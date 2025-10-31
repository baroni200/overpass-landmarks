package com.overpass.landmarks.presentation.controller;

import com.overpass.landmarks.application.dto.LandmarksQueryResponseDto;
import com.overpass.landmarks.application.service.LandmarkQueryService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Controller for landmarks query endpoint.
 * Retrieves landmarks using cache-first strategy with database fallback.
 */
@RestController
@RequestMapping("/landmarks")
@Validated
public class LandmarkController {

    private static final Logger logger = LoggerFactory.getLogger(LandmarkController.class);

    private final LandmarkQueryService landmarkQueryService;

    public LandmarkController(LandmarkQueryService landmarkQueryService) {
        this.landmarkQueryService = landmarkQueryService;
    }

    /**
     * GET /landmarks?lat=X&lng=Y
     * 
     * Query landmarks by coordinates.
     * Strategy: Cache-first → DB fallback → populate cache
     * 
     * @param lat Latitude (-90 to 90)
     * @param lng Longitude (-180 to 180)
     * @return Response with landmarks and source indicator (cache/db/none)
     */
    @GetMapping
    public ResponseEntity<LandmarksQueryResponseDto> getLandmarks(
        @RequestParam @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
        BigDecimal lat,
        
        @RequestParam @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
        BigDecimal lng
    ) {
        logger.info("Querying landmarks: lat={}, lng={}", lat, lng);

        LandmarksQueryResponseDto response = landmarkQueryService.queryLandmarks(lat, lng);
        return ResponseEntity.ok(response);
    }
}

