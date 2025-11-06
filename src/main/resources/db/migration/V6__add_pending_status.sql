-- Add PENDING status to the CHECK constraint
ALTER TABLE coordinate_request DROP CONSTRAINT IF EXISTS coordinate_request_status_check;
ALTER TABLE coordinate_request ADD CONSTRAINT coordinate_request_status_check CHECK (status IN ('PENDING', 'FOUND', 'EMPTY', 'ERROR'));

-- Update comment
COMMENT ON COLUMN coordinate_request.status IS 'Request processing status: PENDING, FOUND, EMPTY, or ERROR';

