-- Seed data for development/testing
-- Inserts reference data that can be used for testing and development
-- Uses ON CONFLICT to allow idempotent execution

-- Seed coordinate request (Eiffel Tower coordinates, rounded to 4 decimals)
INSERT INTO coordinate_request (id, key_lat, key_lng, radius_m, status, requested_at, updated_at)
VALUES 
    ('550e8400-e29b-41d4-a716-446655440000'::UUID,
     '48.8584'::NUMERIC,
     '2.2945'::NUMERIC,
     500,
     'FOUND',
     CURRENT_TIMESTAMP,
     CURRENT_TIMESTAMP)
ON CONFLICT (key_lat, key_lng, radius_m) DO NOTHING;

-- Seed landmarks for the coordinate request above
INSERT INTO landmark (id, coord_request_id, osm_type, osm_id, name, lat, lng, tags, created_at)
VALUES 
    ('550e8400-e29b-41d4-a716-446655440001'::UUID,
     '550e8400-e29b-41d4-a716-446655440000'::UUID,
     'way',
     5013364,
     'Eiffel Tower',
     '48.8584'::NUMERIC,
     '2.2945'::NUMERIC,
     '{"tourism": "attraction", "name": "Eiffel Tower", "wikipedia": "en:Eiffel Tower", "historic": "tower"}'::jsonb,
     CURRENT_TIMESTAMP),
    ('550e8400-e29b-41d4-a716-446655440002'::UUID,
     '550e8400-e29b-41d4-a716-446655440000'::UUID,
     'relation',
     123456,
     'Champ de Mars',
     '48.8566'::NUMERIC,
     '2.2982'::NUMERIC,
     '{"tourism": "attraction", "name": "Champ de Mars", "leisure": "park"}'::jsonb,
     CURRENT_TIMESTAMP)
ON CONFLICT (osm_type, osm_id) DO NOTHING;

COMMENT ON TABLE coordinate_request IS 'Stores coordinate requests with transformed lat/lng keys for idempotency';
