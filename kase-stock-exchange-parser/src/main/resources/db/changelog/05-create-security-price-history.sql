CREATE TABLE security_price_history (
    id                     BIGSERIAL PRIMARY KEY,
    security_instrument_id BIGINT         NOT NULL REFERENCES security_instrument(id) ON DELETE CASCADE,
    price                  NUMERIC(30, 6),
    close_price            NUMERIC(30, 6),
    best_bid               NUMERIC(30, 6),
    best_offer             NUMERIC(30, 6),
    spread                 NUMERIC(30, 6),
    spread_percent         NUMERIC(30, 6),
    volkzt                 NUMERIC(30, 6),
    volusd                 NUMERIC(30, 6),
    dealcnt                INTEGER,
    ytm                    NUMERIC(30, 6),
    dohod                  NUMERIC(30, 6),
    recorded_at            TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_price_history_instrument_time ON security_price_history(security_instrument_id, recorded_at DESC);
CREATE INDEX idx_price_history_time ON security_price_history(recorded_at DESC);
