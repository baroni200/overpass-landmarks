package com.overpass.landmarks.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "landmark", indexes = {
        @Index(name = "idx_landmark_osm", columnList = "osm_type,osm_id", unique = true),
        @Index(name = "idx_landmark_coord_request", columnList = "coord_request_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Landmark {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coord_request_id", nullable = false)
    private CoordinateRequest coordinateRequest;

    @Column(name = "osm_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private OsmType osmType;

    @Column(name = "osm_id", nullable = false)
    private Long osmId;

    @Column(name = "name", length = 500)
    private String name;

    @Column(name = "lat", nullable = false, precision = 9, scale = 6)
    private java.math.BigDecimal lat;

    @Column(name = "lng", nullable = false, precision = 9, scale = 6)
    private java.math.BigDecimal lng;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", columnDefinition = "jsonb")
    private Map<String, Object> tags;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    // Constructor for creating new landmarks
    public Landmark(CoordinateRequest coordinateRequest, OsmType osmType, Long osmId,
            java.math.BigDecimal lat, java.math.BigDecimal lng) {
        this.coordinateRequest = coordinateRequest;
        this.osmType = osmType;
        this.osmId = osmId;
        this.lat = lat;
        this.lng = lng;
    }
}
