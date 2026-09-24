-- Liquibase formatted SQL
-- changeset nurgissa:11-create-dividend-table

CREATE TABLE IF NOT EXISTS dividend_event (
    id BIGSERIAL PRIMARY KEY,
    ticker VARCHAR(32) NOT NULL,
    isin VARCHAR(32),
    company_name VARCHAR(255) NOT NULL,
    record_date DATE NOT NULL,         -- Дата фиксации реестра (отсечка)
    payment_date DATE,                 -- Дата фактической выплаты
    announcement_date DATE,            -- Дата решения СД / ГОСА
    amount_per_share NUMERIC(18, 4) NOT NULL,
    currency VARCHAR(10) DEFAULT 'KZT',
    period VARCHAR(64) NOT NULL,       -- '2025 год', '1 полугодие 2026', 'Q2 2026'
    status VARCHAR(32) DEFAULT 'ANNOUNCED', -- 'ANNOUNCED', 'APPROVED', 'PAID'
    source_url VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_dividend_ticker_record UNIQUE (ticker, record_date, period)
);

CREATE INDEX IF NOT EXISTS idx_dividend_ticker ON dividend_event(UPPER(ticker));
CREATE INDEX IF NOT EXISTS idx_dividend_record_date ON dividend_event(record_date);
CREATE INDEX IF NOT EXISTS idx_dividend_status ON dividend_event(status);

-- Seed initial dividend calendar for key KASE equities
INSERT INTO dividend_event (ticker, isin, company_name, record_date, payment_date, announcement_date, amount_per_share, currency, period, status, source_url)
VALUES
    ('HSBK', 'KZ000A0LE0S4', 'Народный сберегательный банк Казахстана (Halyk Bank)', '2026-05-18', '2026-06-05', '2026-04-20', 27.85, 'KZT', '2025 год', 'PAID', 'https://kase.kz/ru/shares/show/HSBK/'),
    ('HSBK', 'KZ000A0LE0S4', 'Народный сберегательный банк Казахстана (Halyk Bank)', '2026-11-20', '2026-12-08', '2026-09-15', 15.20, 'KZT', '1 полугодие 2026', 'ANNOUNCED', 'https://kase.kz/ru/shares/show/HSBK/'),
    ('KMGZ', 'KZ1C00001122', 'Национальная компания «КазМунайГаз»', '2026-06-01', '2026-06-25', '2026-05-10', 491.71, 'KZT', '2025 год', 'PAID', 'https://kase.kz/ru/shares/show/KMGZ/'),
    ('KZAP', 'KZ1C00001619', 'НАК «Казатомпром»', '2026-07-14', '2026-07-28', '2026-05-25', 1215.00, 'KZT', '2025 год', 'PAID', 'https://kase.kz/ru/shares/show/KZAP/'),
    ('KSPI', 'KZ1C00001536', 'Kaspi.kz', '2026-03-24', '2026-04-10', '2026-02-28', 850.00, 'KZT', '4 кв. 2025', 'PAID', 'https://kase.kz/ru/shares/show/KSPI/'),
    ('KSPI', 'KZ1C00001536', 'Kaspi.kz', '2026-06-25', '2026-07-15', '2026-05-20', 850.00, 'KZT', '1 кв. 2026', 'PAID', 'https://kase.kz/ru/shares/show/KSPI/'),
    ('KSPI', 'KZ1C00001536', 'Kaspi.kz', '2026-10-15', '2026-11-05', '2026-08-25', 850.00, 'KZT', '2 кв. 2026', 'ANNOUNCED', 'https://kase.kz/ru/shares/show/KSPI/'),
    ('KZTK', 'KZ0009093241', 'Казахтелеком', '2026-05-12', '2026-05-29', '2026-04-15', 2096.60, 'KZT', '2025 год', 'PAID', 'https://kase.kz/ru/shares/show/KZTK/'),
    ('KEGC', 'KZ1C00000959', 'KEGOC', '2026-05-25', '2026-06-15', '2026-05-02', 84.72, 'KZT', '2 полугодие 2025', 'PAID', 'https://kase.kz/ru/shares/show/KEGC/'),
    ('KEGC', 'KZ1C00000959', 'KEGOC', '2026-11-10', '2026-11-30', '2026-09-18', 62.50, 'KZT', '1 полугодие 2026', 'ANNOUNCED', 'https://kase.kz/ru/shares/show/KEGC/'),
    ('CCBN', 'KZ0007786572', 'Банк ЦентрКредит', '2026-05-02', '2026-05-20', '2026-04-12', 125.00, 'KZT', '2025 год', 'PAID', 'https://kase.kz/ru/shares/show/CCBN/'),
    ('KZTO', 'KZ1C00000744', 'КазТрансОйл', '2026-06-08', '2026-06-28', '2026-05-18', 65.00, 'KZT', '2025 год', 'PAID', 'https://kase.kz/ru/shares/show/KZTO/'),
    ('AIRA', 'KZ1C00001015', 'Air Astana', '2026-06-15', '2026-07-01', '2026-05-22', 32.40, 'KZT', '2025 год', 'PAID', 'https://kase.kz/ru/shares/show/AIRA/'),
    ('CORE', 'KZ1C00000876', 'Kcell (KCEL/CORE)', '2026-06-20', '2026-07-10', '2026-05-30', 45.00, 'KZT', '2025 год', 'PAID', 'https://kase.kz/ru/shares/show/KCEL/')
ON CONFLICT (ticker, record_date, period) DO UPDATE SET 
    amount_per_share = EXCLUDED.amount_per_share,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;
