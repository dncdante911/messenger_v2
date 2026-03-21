-- ============================================================
-- Business Directory: add columns and indexes for public catalogue
-- ============================================================

-- Allow businesses to opt in/out of the public directory (default: listed)
ALTER TABLE wm_business_profile ADD COLUMN IF NOT EXISTS is_listed TINYINT(1) DEFAULT 1 COMMENT 'Show in public directory';

-- Geo-coordinates for distance-based sorting
ALTER TABLE wm_business_profile ADD COLUMN IF NOT EXISTS lat DECIMAL(10,8) DEFAULT NULL;
ALTER TABLE wm_business_profile ADD COLUMN IF NOT EXISTS lng DECIMAL(11,8) DEFAULT NULL;

-- Indexes for faster directory queries
CREATE INDEX IF NOT EXISTS idx_business_category ON wm_business_profile(category);
CREATE INDEX IF NOT EXISTS idx_business_listed   ON wm_business_profile(is_listed);
