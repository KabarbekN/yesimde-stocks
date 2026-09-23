-- Liquibase formatted SQL
-- changeset nurgissa:10-create-market-reports-tables

CREATE TABLE IF NOT EXISTS security_market_turnover (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    company_name VARCHAR(255),
    sector VARCHAR(128),
    period_code VARCHAR(32) NOT NULL, -- 'CUMULATIVE_2022_2026', 'MONTHLY_2026_08'
    period_start DATE,
    period_end DATE,
    deals_count BIGINT DEFAULT 0,
    volume_kzt NUMERIC(20, 2) DEFAULT 0,
    free_float_pct NUMERIC(6, 2),
    is_ipo_spo BOOLEAN DEFAULT FALSE,
    avg_deal_size_kzt NUMERIC(20, 2) DEFAULT 0,
    participant_type VARCHAR(32), -- 'RETAIL_DOMINATED', 'INSTITUTIONAL_HEAVY', 'BLOCK_DEALS', 'BALANCED'
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_turnover_code_period UNIQUE (code, period_code)
);

CREATE INDEX IF NOT EXISTS idx_turnover_code ON security_market_turnover(UPPER(code));
CREATE INDEX IF NOT EXISTS idx_turnover_period ON security_market_turnover(period_code);
CREATE INDEX IF NOT EXISTS idx_turnover_vol ON security_market_turnover(volume_kzt DESC);

CREATE TABLE IF NOT EXISTS market_benchmark (
    id BIGSERIAL PRIMARY KEY,
    benchmark_key VARCHAR(64) UNIQUE NOT NULL,
    benchmark_name VARCHAR(255) NOT NULL,
    numeric_value NUMERIC(15, 4) NOT NULL,
    unit VARCHAR(32) NOT NULL, -- 'PERCENT', 'KZT', 'POINTS'
    source_name VARCHAR(128),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Seed initial dynamic benchmarks (September 2026)
INSERT INTO market_benchmark (benchmark_key, benchmark_name, numeric_value, unit, source_name)
VALUES 
    ('KZ_BASE_RATE', 'Базовая ставка Национального Банка РК', 16.25, 'PERCENT', 'Национальный Банк РК'),
    ('KZ_INFLATION_RATE', 'Годовая инфляция (ИПЦ)', 8.60, 'PERCENT', 'БНС АСПиР РК'),
    ('DEPOSIT_FLEXIBLE_GESV', 'Средняя ГЭСВ несрочных депозитов (Kaspi/Halyk)', 14.50, 'PERCENT', 'КФГД'),
    ('DEPOSIT_SAVINGS_GESV', 'Максимальная ГЭСВ сберегательных депозитов', 17.50, 'PERCENT', 'КФГД'),
    ('KRISHA_ALMATY_1ROOM_PRICE', 'Медианная цена 1-комн. квартиры в Алматы', 26500000.00, 'KZT', 'Krisha.kz'),
    ('KRISHA_ALMATY_1ROOM_RENT', 'Средняя аренда 1-комн. квартиры в Алматы в месяц', 230000.00, 'KZT', 'Krisha.kz')
ON CONFLICT (benchmark_key) DO UPDATE SET 
    numeric_value = EXCLUDED.numeric_value,
    updated_at = CURRENT_TIMESTAMP;
