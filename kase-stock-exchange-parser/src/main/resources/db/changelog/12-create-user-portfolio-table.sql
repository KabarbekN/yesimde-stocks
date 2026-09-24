-- Liquibase formatted SQL
-- changeset nurgissa:12-create-user-portfolio-table

CREATE TABLE IF NOT EXISTS user_portfolio (
    id BIGSERIAL PRIMARY KEY,
    chat_id BIGINT NOT NULL,
    ticker VARCHAR(32) NOT NULL,
    quantity NUMERIC(18, 4) NOT NULL DEFAULT 1,
    buy_price NUMERIC(18, 4), -- Средняя цена покупки (себестоимость)
    currency VARCHAR(10) DEFAULT 'KZT',
    notes VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_portfolio_chat_ticker UNIQUE (chat_id, ticker)
);

CREATE INDEX IF NOT EXISTS idx_portfolio_chat_id ON user_portfolio(chat_id);
CREATE INDEX IF NOT EXISTS idx_portfolio_ticker ON user_portfolio(UPPER(ticker));
