package com.overpass.landmarks.infrastructure.persistence;

import com.overpass.landmarks.domain.model.CoordinateRequest;
import com.overpass.landmarks.domain.model.Landmark;
import com.overpass.landmarks.domain.model.OsmType;
import com.overpass.landmarks.domain.model.RequestStatus;
import com.overpass.landmarks.domain.repository.CoordinateRequestRepository;
import com.overpass.landmarks.domain.repository.LandmarkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Data seeder for local development.
 * Runs when 'local' profile is active and app.seeding.enabled=true
 * to populate additional test data beyond Flyway migrations.
 */
@Configuration
public class DataSeeder {

    private static final Logger logger = LoggerFactory.getLogger(DataSeeder.class);

    @Bean
    @ConditionalOnProperty(name = "app.seeding.enabled", havingValue = "true", matchIfMissing = false)
    public CommandLineRunner seedLocalData(
        CoordinateRequestRepository coordinateRequestRepository,
        LandmarkRepository landmarkRepository
    ) {
        return args -> {
            // Check if seed data already exists (beyond Flyway seed)
            long existingRequests = coordinateRequestRepository.count();
            if (existingRequests > 1) { // More than the Flyway seed
                logger.info("Local seed data already exists ({} requests), skipping...", existingRequests);
                return;
            }

            logger.info("Seeding local development data...");

            // Create additional test coordinate request (Notre-Dame coordinates)
            BigDecimal testLat = new BigDecimal("48.8530");
            BigDecimal testLng = new BigDecimal("2.3499");
            Integer radius = 500;

            // Check if this coordinate request already exists
            boolean exists = coordinateRequestRepository.existsByKeyLatAndKeyLngAndRadiusMeters(
                testLat, testLng, radius
            );

            if (!exists) {
                CoordinateRequest coordinateRequest = new CoordinateRequest(testLat, testLng, radius);
                coordinateRequest.setStatus(RequestStatus.FOUND);
                coordinateRequest = coordinateRequestRepository.save(coordinateRequest);

                // Create test landmark
                Landmark landmark = new Landmark(
                    coordinateRequest,
                    OsmType.way,
                    5013000L,
                    new BigDecimal("48.8530"),
                    new BigDecimal("2.3499")
                );
                landmark.setName("Notre-Dame de Paris");
                landmark.setTags(Map.of(
                    "tourism", "attraction",
                    "name", "Notre-Dame de Paris",
                    "wikipedia", "en:Notre-Dame de Paris",
                    "historic", "cathedral"
                ));
                landmarkRepository.save(landmark);

                logger.info("Local seed data created: Notre-Dame test coordinates");
            } else {
                logger.debug("Test coordinate request already exists, skipping");
            }

            logger.info("Local seeding complete");
        };
    }
}

