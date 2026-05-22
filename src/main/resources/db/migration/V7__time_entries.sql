CREATE TABLE time_entries (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    toggl_id          BIGINT       NOT NULL UNIQUE,
    user_id           BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    toggl_user_id     BIGINT,
    workspace_id      BIGINT       NOT NULL,
    project_id        BIGINT,
    task_id           BIGINT,
    description       TEXT,
    start             TIMESTAMPTZ  NOT NULL,
    stop              TIMESTAMPTZ,
    duration          BIGINT       NOT NULL,
    billable          BOOLEAN      NOT NULL DEFAULT FALSE,
    tags              JSONB        NOT NULL DEFAULT '[]'::jsonb,
    created_with      VARCHAR(255),
    toggl_at          TIMESTAMPTZ,
    server_deleted_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_time_entries_tags_is_array
        CHECK (jsonb_typeof(tags) = 'array')
);

CREATE INDEX idx_time_entries_user_id_start ON time_entries (user_id, start DESC);
CREATE INDEX idx_time_entries_workspace_id  ON time_entries (workspace_id);
CREATE INDEX idx_time_entries_project_id    ON time_entries (project_id);
CREATE INDEX idx_time_entries_tags          ON time_entries USING GIN (tags jsonb_path_ops);
