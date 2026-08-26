ALTER TABLE user_settings
    ADD COLUMN time_zone VARCHAR(64) NOT NULL DEFAULT 'America/Chicago';
