package com.overpass.landmarks.module.test.support;

import com.overpass.landmarks.application.port.out.CoordinateRequestRepository;
import com.overpass.landmarks.application.port.out.LandmarkRepository;
import com.overpass.landmarks.domain.model.CoordinateRequest;
import com.overpass.landmarks.domain.model.Landmark;
import com.overpass.landmarks.domain.model.RequestStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Test data builder for integration tests.
 * Provides fluent API for building test data.
 */
@Component
public class TestDataBuilder {

    private final CoordinateRequestRepository coordinateRequestRepository;
    private final LandmarkRepository landmarkRepository;

    public TestDataBuilder(
            CoordinateRequestRepository coordinateRequestRepository,
            LandmarkRepository landmarkRepository) {
        this.coordinateRequestRepository = coordinateRequestRepository;
        this.landmarkRepository = landmarkRepository;
    }

    /**
     * Builder for coordinate request test data.
     */
    public CoordinateRequestBuilder coordinateRequest() {
        return new CoordinateRequestBuilder();
    }

    /**
     * Fluent builder for coordinate requests.
     */
    public class CoordinateRequestBuilder {
        private BigDecimal lat;
        private BigDecimal lng;
        private Integer radiusMeters = 500;
        private RequestStatus status = RequestStatus.FOUND;

        public CoordinateRequestBuilder withLat(BigDecimal lat) {
            this.lat = lat;
            return this;
        }

        public CoordinateRequestBuilder withLng(BigDecimal lng) {
            this.lng = lng;
            return this;
        }

        public CoordinateRequestBuilder withRadiusMeters(Integer radiusMeters) {
            this.radiusMeters = radiusMeters;
            return this;
        }

        public CoordinateRequestBuilder withStatus(RequestStatus status) {
            this.status = status;
            return this;
        }

        public CoordinateRequest build() {
            if (lat == null || lng == null) {
                throw new IllegalStateException("Lat and lng are required");
            }
            CoordinateRequest request = new CoordinateRequest(lat, lng, radiusMeters);
            request.setStatus(status);
            return coordinateRequestRepository.save(request);
        }

        public CoordinateRequestWithLandmarksBuilder withLandmarks() {
            return new CoordinateRequestWithLandmarksBuilder(this);
        }
    }

    /**
     * Builder for coordinate request with landmarks.
     */
    public class CoordinateRequestWithLandmarksBuilder {
        private final CoordinateRequestBuilder requestBuilder;
        private CoordinateRequest savedRequest;

        public CoordinateRequestWithLandmarksBuilder(CoordinateRequestBuilder requestBuilder) {
            this.requestBuilder = requestBuilder;
        }

        public CoordinateRequestWithLandmarksBuilder landmark(Landmark landmark) {
            if (savedRequest == null) {
                savedRequest = requestBuilder.build();
            }
            landmark.setCoordinateRequest(savedRequest);
            landmarkRepository.save(landmark);
            return this;
        }

        public CoordinateRequest build() {
            if (savedRequest == null) {
                savedRequest = requestBuilder.build();
            }
            return savedRequest;
        }
    }
}

