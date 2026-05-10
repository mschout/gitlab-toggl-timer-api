CREATE TABLE users (
    id            BIGSERIAL    PRIMARY KEY,
    email         VARCHAR(320) NOT NULL UNIQUE,
    display_name  VARCHAR(255),
    password_hash VARCHAR(255),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE user_roles (
    user_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(64) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE user_auth_identities (
    id         BIGSERIAL    PRIMARY KEY,
    provider   VARCHAR(64)  NOT NULL,
    subject    VARCHAR(255) NOT NULL,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_auth_identity_provider_subject UNIQUE (provider, subject)
);

CREATE INDEX idx_user_auth_identities_user_id ON user_auth_identities (user_id);
