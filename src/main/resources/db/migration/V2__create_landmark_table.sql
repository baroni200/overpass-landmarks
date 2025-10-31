CREATE TABLE landmark (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coord_request_id UUID NOT NULL REFERENCES coordinate_request(id) ON DELETE CASCADE,
    osm_type VARCHAR(20) NOT NULL CHECK (osm_type IN ('way', 'relation', 'node')),
    osm_id BIGINT NOT NULL,
    name VARCHAR(500),
    lat NUMERIC(9,6) NOT NULL,
    lng NUMERIC(9,6) NOT NULL,
    tags JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_landmark_osm UNIQUE (osm_type, osm_id)
);

CREATE INDEX idx_landmark_osm ON landmark(osm_type, osm_id);
CREATE INDEX idx_landmark_coord_request ON landmark(coord_request_id);
CREATE INDEX idx_landmark_location ON landmark(lat, lng);

COMMENT ON TABLE landmark IS 'Stores landmarks retrieved from Overpass API';
COMMENT ON COLUMN landmark.osm_type IS 'OSM element type: way, relation, or node';
COMMENT ON COLUMN landmark.osm_id IS 'OSM element ID';
COMMENT ON COLUMN landmark.tags IS 'JSONB column storing all OSM tags';

