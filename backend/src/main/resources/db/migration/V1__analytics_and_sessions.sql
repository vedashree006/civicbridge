-- CivicBridge analytics + session registry. No FK to users: on a fresh database Hibernate
-- creates `users` after Flyway runs. user_id is validated by the application.
CREATE TABLE IF NOT EXISTS user_sessions (
    id            VARCHAR(36)  PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    last_seen_at  TIMESTAMPTZ  NOT NULL,
    expires_at    TIMESTAMPTZ  NOT NULL,
    ended_at      TIMESTAMPTZ,
    end_reason    VARCHAR(20)
);
CREATE INDEX IF NOT EXISTS idx_user_sessions_user ON user_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_user_sessions_open ON user_sessions(ended_at, expires_at);

CREATE TABLE IF NOT EXISTS analytics_events (
    id          BIGSERIAL    PRIMARY KEY,
    event_type  VARCHAR(40)  NOT NULL,
    user_id     BIGINT,
    session_id  VARCHAR(36),
    path        VARCHAR(200),
    visitor_hash VARCHAR(64),
    occurred_at TIMESTAMPTZ  NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_ae_time ON analytics_events(occurred_at);
CREATE INDEX IF NOT EXISTS idx_ae_type_time ON analytics_events(event_type, occurred_at);

CREATE TABLE IF NOT EXISTS consent_records (
    id              BIGSERIAL    PRIMARY KEY,
    user_id         BIGINT,
    policy_version  VARCHAR(20)  NOT NULL,
    preferences     BOOLEAN      NOT NULL,
    analytics       BOOLEAN      NOT NULL,
    marketing       BOOLEAN      NOT NULL,
    recorded_at     TIMESTAMPTZ  NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_consent_time ON consent_records(recorded_at);
