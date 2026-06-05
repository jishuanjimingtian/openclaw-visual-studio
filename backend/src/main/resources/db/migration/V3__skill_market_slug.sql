ALTER TABLE installed_skills ADD COLUMN IF NOT EXISTS market_slug VARCHAR(200);
CREATE INDEX IF NOT EXISTS idx_installed_skills_market_slug ON installed_skills (market_slug);
