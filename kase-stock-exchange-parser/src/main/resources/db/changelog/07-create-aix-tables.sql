--liquibase formatted sql

--changeset nurgissa:07-1
CREATE TABLE IF NOT EXISTS aix_security_instrument (
    sec_code                VARCHAR(50) PRIMARY KEY,
    isin                    VARCHAR(50),
    issuer                  VARCHAR(255),
    short_name              VARCHAR(100),
    instrument              VARCHAR(100),
    segment                 VARCHAR(50),
    asset_class             VARCHAR(20),
    security_group          VARCHAR(20),
    currency                VARCHAR(10),
    state                   VARCHAR(20),
    reference_price         NUMERIC(30, 6),
    bid_price               NUMERIC(30, 6),
    bid_qty                 BIGINT,
    offer_price             NUMERIC(30, 6),
    offer_qty               BIGINT,
    last_trade              NUMERIC(30, 6),
    previous_close          NUMERIC(30, 6),
    average_weighted_price  NUMERIC(30, 6),
    percent_change          NUMERIC(30, 6),
    price_change            NUMERIC(30, 6),
    volume                  NUMERIC(30, 6),
    value                   NUMERIC(30, 6),
    number_of_trades        INTEGER,
    nav                     NUMERIC(30, 6),
    nav_currency            VARCHAR(10),
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_aix_isin ON aix_security_instrument (isin);
CREATE INDEX IF NOT EXISTS idx_aix_asset_class ON aix_security_instrument (asset_class);
