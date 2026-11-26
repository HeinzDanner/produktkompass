ALTER TABLE app_users ADD COLUMN recovery_email VARCHAR(254);
ALTER TABLE app_users ADD COLUMN recovery_email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_users ADD COLUMN verification_token_hash VARCHAR(64) UNIQUE;
ALTER TABLE app_users ADD COLUMN verification_expires_at TIMESTAMP;
ALTER TABLE app_users ADD COLUMN verification_sent_at TIMESTAMP;
ALTER TABLE app_users ADD COLUMN reset_token_hash VARCHAR(64) UNIQUE;
ALTER TABLE app_users ADD COLUMN reset_expires_at TIMESTAMP;
ALTER TABLE app_users ADD COLUMN credential_version BIGINT NOT NULL DEFAULT 0;
