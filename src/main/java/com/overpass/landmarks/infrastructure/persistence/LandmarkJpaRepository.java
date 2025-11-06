package com.overpass.landmarks.infrastructure.persistence;

import com.overpass.landmarks.application.port.out.LandmarkRepository;
import com.overpass.landmarks.domain.model.Landmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * JPA implementation of LandmarkRepository output port.
 */
@Repository
public interface LandmarkJpaRepository extends JpaRepository<Landmark, UUID>, LandmarkRepository {

    /**
     * Find all landmarks for a given coordinate request.
     * Excludes soft-deleted records.
     */
    @Override
    @Query("SELECT l FROM Landmark l WHERE l.coordinateRequest.id = :coordinateRequestId AND l.deletedAt IS NULL")
    List<Landmark> findByCoordinateRequestId(@Param("coordinateRequestId") UUID coordinateRequestId);

    /**
     * Find landmarks by coordinate request key (for cache miss lookups).
     * Excludes soft-deleted records.
     */
    @Override
    @Query("SELECT l FROM Landmark l WHERE l.coordinateRequest.keyLat = :keyLat AND l.coordinateRequest.keyLng = :keyLng AND l.coordinateRequest.radiusMeters = :radiusMeters AND l.deletedAt IS NULL")
    List<Landmark> findByCoordinateRequestKey(
        @Param("keyLat") java.math.BigDecimal keyLat,
        @Param("keyLng") java.math.BigDecimal keyLng,
        @Param("radiusMeters") Integer radiusMeters
    );

    /**
     * Find a landmark by OSM type and ID.
     * Excludes soft-deleted records.
     */
    @Query("SELECT l FROM Landmark l WHERE l.osmType = :osmType AND l.osmId = :osmId AND l.deletedAt IS NULL")
    java.util.Optional<Landmark> findByOsmTypeAndOsmId(
        @Param("osmType") com.overpass.landmarks.domain.model.OsmType osmType,
        @Param("osmId") Long osmId
    );
}

