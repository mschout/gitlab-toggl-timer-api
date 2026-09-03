CREATE TABLE time_entry_split_operations (
    id                     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    original_toggl_id      BIGINT       NOT NULL,
    workspace_id           BIGINT       NOT NULL,
    project_id             BIGINT,
    task_id                BIGINT,
    description            TEXT,
    original_start         TIMESTAMPTZ  NOT NULL,
    original_stop          TIMESTAMPTZ  NOT NULL,
    split_at               TIMESTAMPTZ  NOT NULL,
    billable               BOOLEAN      NOT NULL,
    tags                   JSONB        NOT NULL DEFAULT '[]'::jsonb,
    created_with           VARCHAR(255) NOT NULL,
    first_child_toggl_id   BIGINT,
    second_child_toggl_id  BIGINT,
    phase                  VARCHAR(32)  NOT NULL,
    last_error             TEXT,
    attempt_count          INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    lease_until            TIMESTAMPTZ,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_time_entry_split_user_original UNIQUE (user_id, original_toggl_id),
    CONSTRAINT chk_time_entry_split_tags_is_array CHECK (jsonb_typeof(tags) = 'array'),
    CONSTRAINT chk_time_entry_split_interval CHECK (
        original_start < split_at AND split_at < original_stop
    )
);

CREATE INDEX idx_time_entry_split_due
    ON time_entry_split_operations (next_attempt_at, lease_until)
    WHERE phase <> 'NEEDS_REVIEW';
