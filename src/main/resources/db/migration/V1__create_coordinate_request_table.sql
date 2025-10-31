CREATE TABLE coordinate_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key_lat NUMERIC(9,6) NOT NULL,
    key_lng NUMERIC(9,6) NOT NULL,
    radius_m INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('FOUND', 'EMPTY', 'ERROR')),
    error_message VARCHAR(1000),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_coordinate_request_key UNIQUE (key_lat, key_lng, radius_m)
);

CREATE INDEX idx_coordinate_request_key ON coordinate_request(key_lat, key_lng, radius_m);
CREATE INDEX idx_coordinate_request_requested_at ON coordinate_request(requested_at);

COMMENT ON TABLE coordinate_request IS 'Stores coordinate requests with transformed lat/lng keys for idempotency';
COMMENT ON COLUMN coordinate_request.key_lat IS 'Transformed latitude (rounded to 4 decimals)';
COMMENT ON COLUMN coordinate_request.key_lng IS 'Transformed longitude (rounded to 4 decimals)';
COMMENT ON COLUMN coordinate_request.status IS 'Request processing status: FOUND, EMPTY, or ERROR';

