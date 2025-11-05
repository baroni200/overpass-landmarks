-- Add deleted_at column to landmark table for soft delete
ALTER TABLE landmark ADD COLUMN deleted_at TIMESTAMPTZ;

-- Update unique constraint to exclude soft-deleted records
-- First, drop the existing constraint
ALTER TABLE landmark DROP CONSTRAINT uk_landmark_osm;

-- Create a partial unique index that excludes soft-deleted records
CREATE UNIQUE INDEX uk_landmark_osm ON landmark(osm_type, osm_id) 
WHERE deleted_at IS NULL;

-- Add index for soft delete queries
CREATE INDEX idx_landmark_deleted_at ON landmark(deleted_at);

COMMENT ON COLUMN landmark.deleted_at IS 'Timestamp when record was soft deleted. NULL means not deleted.';

