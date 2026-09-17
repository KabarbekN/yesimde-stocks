--liquibase formatted sql

--changeset nurgissa:08-1
CREATE TABLE IF NOT EXISTS price_alert_target (
    id              BIGSERIAL PRIMARY KEY,
    chat_id         BIGINT NOT NULL,
    ticker          VARCHAR(50) NOT NULL,
    target_price    NUMERIC(30, 6) NOT NULL,
    direction       VARCHAR(10) NOT NULL,
    initial_price   NUMERIC(30, 6),
    is_triggered    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    triggered_at    TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_price_alert_target_active ON price_alert_target (ticker, is_triggered);
CREATE INDEX IF NOT EXISTS idx_price_alert_target_chat ON price_alert_target (chat_id, is_triggered);

--changeset nurgissa:08-2
ALTER TABLE telegram_subscriber ADD COLUMN IF NOT EXISTS price_change_threshold NUMERIC(5, 2) DEFAULT 3.00;
