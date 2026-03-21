-- Add album_id for grouping media messages into albums
ALTER TABLE Wo_Messages ADD COLUMN IF NOT EXISTS album_id BIGINT(20) DEFAULT NULL COMMENT 'Groups consecutive media messages into an album';
CREATE INDEX IF NOT EXISTS idx_album_id ON Wo_Messages(album_id);
