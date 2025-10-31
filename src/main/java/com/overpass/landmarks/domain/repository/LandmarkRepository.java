package com.overpass.landmarks.domain.repository;

import com.overpass.landmarks.domain.model.Landmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LandmarkRepository extends JpaRepository<Landmark, UUID> {

    /**
     * Find all landmarks for a given coordinate request.
     */
    List<Landmark> findByCoordinateRequestId(UUID coordinateRequestId);

    /**
     * Find landmarks by coordinate request key (for cache miss lookups).
     */
    @Query("SELECT l FROM Landmark l WHERE l.coordinateRequest.keyLat = :keyLat AND l.coordinateRequest.keyLng = :keyLng AND l.coordinateRequest.radiusMeters = :radiusMeters")
    List<Landmark> findByCoordinateRequestKey(
        @Param("keyLat") java.math.BigDecimal keyLat,
        @Param("keyLng") java.math.BigDecimal keyLng,
        @Param("radiusMeters") Integer radiusMeters
    );
}

