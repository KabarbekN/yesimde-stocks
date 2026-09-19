--liquibase formatted sql

--changeset nurgissa:09-1
CREATE INDEX IF NOT EXISTS idx_sec_inst_code_upper ON security_instrument(UPPER(code));
CREATE INDEX IF NOT EXISTS idx_sec_inst_type_vol ON security_instrument(sec_type, volkzt DESC NULLS LAST);
CREATE INDEX IF NOT EXISTS idx_sec_inst_type_dtm ON security_instrument(sec_type, dtm, dohod);

--changeset nurgissa:09-2
CREATE INDEX IF NOT EXISTS idx_ticker_sec_inst_id ON ticker(security_instrument_id);
CREATE INDEX IF NOT EXISTS idx_ticker_nin_upper ON ticker(UPPER(nin));
CREATE INDEX IF NOT EXISTS idx_ticker_nin2_upper ON ticker(UPPER(nin2));

--changeset nurgissa:09-3
CREATE INDEX IF NOT EXISTS idx_aix_sec_code ON aix_security_instrument(sec_code);
CREATE INDEX IF NOT EXISTS idx_aix_asset_class ON aix_security_instrument(asset_class);
