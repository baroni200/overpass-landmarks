package com.overpass.landmarks.infrastructure.persistence;

import com.overpass.landmarks.application.port.out.CoordinateRequestRepository;
import com.overpass.landmarks.domain.model.CoordinateRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA implementation of CoordinateRequestRepository output port.
 */
@Repository
public interface CoordinateRequestJpaRepository
        extends JpaRepository<CoordinateRequest, UUID>, CoordinateRequestRepository {

    /**
     * Find coordinate request by transformed key and radius.
     * Used for idempotency checks and cache miss lookups.
     * Excludes soft-deleted records.
     */
    @Override
    @Query("SELECT cr FROM CoordinateRequest cr WHERE cr.keyLat = :keyLat AND cr.keyLng = :keyLng AND cr.radiusMeters = :radiusMeters AND cr.deletedAt IS NULL")
    Optional<CoordinateRequest> findByKeyLatAndKeyLngAndRadiusMeters(
            @Param("keyLat") BigDecimal keyLat,
            @Param("keyLng") BigDecimal keyLng,
            @Param("radiusMeters") Integer radiusMeters);

    /**
     * Check if a coordinate request exists for the given key.
     * Excludes soft-deleted records.
     */
    @Override
    @Query("SELECT COUNT(cr) > 0 FROM CoordinateRequest cr WHERE cr.keyLat = :keyLat AND cr.keyLng = :keyLng AND cr.radiusMeters = :radiusMeters AND cr.deletedAt IS NULL")
    boolean existsByKeyLatAndKeyLngAndRadiusMeters(
            @Param("keyLat") BigDecimal keyLat,
            @Param("keyLng") BigDecimal keyLng,
            @Param("radiusMeters") Integer radiusMeters);

    /**
     * Find coordinate request by ID, excluding soft-deleted records.
     */
    @Query("SELECT cr FROM CoordinateRequest cr WHERE cr.id = :id AND cr.deletedAt IS NULL")
    Optional<CoordinateRequest> findByIdNotDeleted(@Param("id") UUID id);
}
