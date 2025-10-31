package com.overpass.landmarks.domain.repository;

import com.overpass.landmarks.domain.model.CoordinateRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CoordinateRequestRepository extends JpaRepository<CoordinateRequest, UUID> {

    /**
     * Find coordinate request by transformed key and radius.
     * Used for idempotency checks and cache miss lookups.
     */
    Optional<CoordinateRequest> findByKeyLatAndKeyLngAndRadiusMeters(
        BigDecimal keyLat,
        BigDecimal keyLng,
        Integer radiusMeters
    );

    /**
     * Check if a coordinate request exists for the given key.
     */
    boolean existsByKeyLatAndKeyLngAndRadiusMeters(
        BigDecimal keyLat,
        BigDecimal keyLng,
        Integer radiusMeters
    );
}

