CREATE TABLE workspaces (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    toggl_id   BIGINT       NOT NULL UNIQUE,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
