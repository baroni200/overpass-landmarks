package com.overpass.landmarks.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "coordinate_request", indexes = {
        @Index(name = "idx_coordinate_request_key", columnList = "key_lat,key_lng,radius_m", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class CoordinateRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "key_lat", nullable = false, precision = 9, scale = 6)
    private java.math.BigDecimal keyLat;

    @Column(name = "key_lng", nullable = false, precision = 9, scale = 6)
    private java.math.BigDecimal keyLng;

    @Column(name = "radius_m", nullable = false)
    private Integer radiusMeters;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private RequestStatus status;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    // Constructor for creating new coordinate requests
    public CoordinateRequest(java.math.BigDecimal keyLat, java.math.BigDecimal keyLng, Integer radiusMeters) {
        this.keyLat = keyLat;
        this.keyLng = keyLng;
        this.radiusMeters = radiusMeters;
        this.status = RequestStatus.FOUND;
        // Set timestamps in constructor to ensure they're always set
        this.requestedAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * Pre-persist callback to ensure timestamps are set if auditing doesn't handle
     * it.
     * This is a fallback for test environments where auditing may not work
     * correctly.
     */
    @PrePersist
    protected void onCreate() {
        if (requestedAt == null) {
            requestedAt = OffsetDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = OffsetDateTime.now();
        }
    }

    /**
     * Pre-update callback to update the modified timestamp.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
