--liquibase formatted sql

--changeset nurgissa:06-1
CREATE TABLE IF NOT EXISTS telegram_subscriber (
    chat_id         BIGINT PRIMARY KEY,
    username        VARCHAR(255),
    first_name      VARCHAR(255),
    sub_new_bonds   BOOLEAN NOT NULL DEFAULT TRUE,
    sub_discounts   BOOLEAN NOT NULL DEFAULT TRUE,
    sub_whales      BOOLEAN NOT NULL DEFAULT TRUE,
    sub_coupons     BOOLEAN NOT NULL DEFAULT TRUE,
    sub_stocks      BOOLEAN NOT NULL DEFAULT TRUE,
    watchlist       TEXT DEFAULT 'KSPI,HSBK,KZAP,AIRA,KMGZ',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

--changeset nurgissa:06-2
CREATE TABLE IF NOT EXISTS alert_cooldown (
    id              BIGSERIAL PRIMARY KEY,
    alert_type      VARCHAR(50) NOT NULL,
    ticker          VARCHAR(50) NOT NULL,
    last_sent_at    TIMESTAMP NOT NULL,
    last_value      NUMERIC(30, 6),
    CONSTRAINT uk_alert_cooldown UNIQUE (alert_type, ticker)
);

CREATE INDEX IF NOT EXISTS idx_alert_cooldown_lookup ON alert_cooldown (alert_type, ticker);
