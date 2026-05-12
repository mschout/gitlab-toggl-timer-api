-- TOTP authenticator devices. Multiple per user; secret column is encrypted at rest.
CREATE TABLE totp_credentials (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    label        VARCHAR(100) NOT NULL,
    secret       TEXT NOT NULL,
    confirmed    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    last_used_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_totp_user ON totp_credentials (user_id);

-- One-time recovery codes generated when MFA is enrolled. code_hash is bcrypt.
CREATE TABLE recovery_codes (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash  VARCHAR(100) NOT NULL,
    used_at    TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX idx_recovery_user_unused ON recovery_codes (user_id) WHERE used_at IS NULL;

-- Schemas below match Spring Security 7.0.5 JdbcPublicKeyCredentialUserEntityRepository
-- and JdbcUserCredentialRepository expectations. Do not rename columns.
CREATE TABLE user_entities (
    id           VARCHAR(1000) PRIMARY KEY,
    name         VARCHAR(1000) NOT NULL,
    display_name VARCHAR(1000)
);
CREATE INDEX idx_user_entities_name ON user_entities (name);

CREATE TABLE user_credentials (
    credential_id                VARCHAR(1000) PRIMARY KEY,
    user_entity_user_id          VARCHAR(1000) NOT NULL,
    public_key                   BYTEA NOT NULL,
    signature_count              BIGINT,
    uv_initialized               BOOLEAN,
    backup_eligible              BOOLEAN NOT NULL,
    authenticator_transports     VARCHAR(1000),
    public_key_credential_type   VARCHAR(100),
    backup_state                 BOOLEAN NOT NULL,
    attestation_object           BYTEA,
    attestation_client_data_json BYTEA,
    created                      TIMESTAMP,
    last_used                    TIMESTAMP,
    label                        VARCHAR(1000) NOT NULL
);
CREATE INDEX idx_user_credentials_user_id ON user_credentials (user_entity_user_id);
