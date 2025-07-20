CREATE TABLE instrument_market_maker (
                                         security_instrument_id BIGINT   NOT NULL
                                             REFERENCES security_instrument(id)
                                                 ON DELETE CASCADE,
                                         org_code               VARCHAR(50) NOT NULL
                                             REFERENCES market_maker(org_code)
                                                 ON DELETE CASCADE,
                                         PRIMARY KEY (security_instrument_id, org_code)
);
