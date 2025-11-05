package com.overpass.landmarks.infrastructure.persistence;

import com.overpass.landmarks.application.port.out.CoordinateRequestRepository;
import com.overpass.landmarks.application.port.out.LandmarkRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for wiring JPA repositories to application ports.
 * This adapter layer bridges infrastructure (JPA) with application ports.
 */
@Configuration
public class PersistenceAdapterConfig {

    /**
     * Wire JPA repository to CoordinateRequestRepository port.
     */
    @Bean
    public CoordinateRequestRepository coordinateRequestRepository(CoordinateRequestJpaRepository jpaRepository) {
        return jpaRepository;
    }

    /**
     * Wire JPA repository to LandmarkRepository port.
     */
    @Bean
    public LandmarkRepository landmarkRepository(LandmarkJpaRepository jpaRepository) {
        return jpaRepository;
    }
}

