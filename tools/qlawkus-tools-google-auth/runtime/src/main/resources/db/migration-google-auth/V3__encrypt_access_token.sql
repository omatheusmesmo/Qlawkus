ALTER TABLE google_credential ADD COLUMN IF NOT EXISTS encrypted_access_token TEXT;
ALTER TABLE google_credential DROP COLUMN IF EXISTS access_token;
