-- V1: Initial schema

CREATE TABLE users (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username   VARCHAR(64)  NOT NULL UNIQUE,
    email      VARCHAR(255) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE social_accounts (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform     VARCHAR(32)  NOT NULL,
    account_id   VARCHAR(255) NOT NULL,
    access_token TEXT         NOT NULL,
    token_secret TEXT,
    active       BOOLEAN      NOT NULL DEFAULT true,
    created_at   TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT now(),
    UNIQUE (user_id, platform, account_id)
);

CREATE TABLE media_files (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    s3_key        VARCHAR(512) NOT NULL UNIQUE,
    original_name VARCHAR(255) NOT NULL,
    media_type    VARCHAR(16)  NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE publish_logs (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    media_file_id      UUID         NOT NULL REFERENCES media_files(id) ON DELETE CASCADE,
    social_account_id  UUID         NOT NULL REFERENCES social_accounts(id) ON DELETE CASCADE,
    platform           VARCHAR(32)  NOT NULL,
    external_post_id   VARCHAR(512),
    title              VARCHAR(512),
    description        TEXT,
    status             VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    error_message      TEXT,
    published_at       TIMESTAMP,
    removed_at         TIMESTAMP,
    created_at         TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE scheduled_publishes (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    media_file_id     UUID         NOT NULL REFERENCES media_files(id) ON DELETE CASCADE,
    social_account_id UUID         NOT NULL REFERENCES social_accounts(id) ON DELETE CASCADE,
    title             VARCHAR(512),
    description       TEXT,
    frequency         VARCHAR(16)  NOT NULL,
    active            BOOLEAN      NOT NULL DEFAULT true,
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_social_accounts_user ON social_accounts(user_id);
CREATE INDEX idx_media_files_user     ON media_files(user_id);
CREATE INDEX idx_publish_logs_user    ON publish_logs(user_id);
CREATE INDEX idx_publish_logs_file    ON publish_logs(media_file_id);
CREATE INDEX idx_scheduled_active     ON scheduled_publishes(active) WHERE active = true;
