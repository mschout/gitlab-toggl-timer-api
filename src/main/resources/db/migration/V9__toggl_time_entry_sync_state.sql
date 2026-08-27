CREATE TABLE toggl_time_entry_sync_state (
    user_id                 BIGINT      PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    last_successful_sync_at TIMESTAMPTZ NOT NULL
);
