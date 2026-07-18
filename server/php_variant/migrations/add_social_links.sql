-- Add social link columns to users table (run once on production)
ALTER TABLE users ADD COLUMN IF NOT EXISTS whatsapp TEXT DEFAULT '';
ALTER TABLE users ADD COLUMN IF NOT EXISTS facebook TEXT DEFAULT '';
ALTER TABLE users ADD COLUMN IF NOT EXISTS instagram TEXT DEFAULT '';
