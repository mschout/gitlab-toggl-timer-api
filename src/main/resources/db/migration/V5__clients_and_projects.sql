CREATE TABLE clients (
    id           BIGSERIAL    PRIMARY KEY,
    toggl_id     BIGINT       NOT NULL UNIQUE,
    workspace_id BIGINT       NOT NULL,
    name         VARCHAR(255) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_clients_workspace_id ON clients (workspace_id);

CREATE TABLE projects (
    id              BIGSERIAL    PRIMARY KEY,
    toggl_id        BIGINT       NOT NULL UNIQUE,
    workspace_id    BIGINT       NOT NULL,
    toggl_client_id BIGINT,
    name            VARCHAR(255) NOT NULL,
    color           VARCHAR(16),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_projects_workspace_id    ON projects (workspace_id);
CREATE INDEX idx_projects_toggl_client_id ON projects (toggl_client_id);
