CREATE TABLE user_settings (
    user_id                       BIGINT      PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    gitlab_access_token_encrypted TEXT,
    toggl_api_key_encrypted       TEXT,
    created_at                    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                    TIMESTAMPTZ NOT NULL DEFAULT now()
);
