-- Add deleted_at column to coordinate_request table for soft delete
ALTER TABLE coordinate_request ADD COLUMN deleted_at TIMESTAMPTZ;

-- Update unique constraint to exclude soft-deleted records
-- First, drop the existing constraint
ALTER TABLE coordinate_request DROP CONSTRAINT uk_coordinate_request_key;

-- Create a partial unique index that excludes soft-deleted records
CREATE UNIQUE INDEX uk_coordinate_request_key ON coordinate_request(key_lat, key_lng, radius_m) 
WHERE deleted_at IS NULL;

-- Add index for soft delete queries
CREATE INDEX idx_coordinate_request_deleted_at ON coordinate_request(deleted_at);

COMMENT ON COLUMN coordinate_request.deleted_at IS 'Timestamp when record was soft deleted. NULL means not deleted.';

