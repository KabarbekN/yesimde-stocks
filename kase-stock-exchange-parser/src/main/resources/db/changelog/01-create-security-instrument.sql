CREATE TABLE security_instrument (
                                     id                     BIGINT       NOT NULL ,
                                     code                   VARCHAR(50)  NOT NULL,
                                     sec_type               VARCHAR(50),
                                     price                  NUMERIC(30, 6),
                                     close_price            NUMERIC(30, 6),
                                     best_bid               NUMERIC(30, 6),
                                     best_offer             NUMERIC(30, 6),
                                     volkzt                 NUMERIC(30, 6),
                                     volusd                 NUMERIC(30, 6),
                                     capit                  NUMERIC(30, 6),
                                     date0                  TIMESTAMP,
                                     trand                  NUMERIC(30,6),
                                     trand_percent          NUMERIC(30,6),
                                     org_code               VARCHAR(50),
                                     org_name_ru            VARCHAR(255),
                                     org_name_en            VARCHAR(255),
                                     org_name_kz            VARCHAR(255),
                                     org_short_name_ru      VARCHAR(255),
                                     org_short_name_en      VARCHAR(255),
                                     org_short_name_kz      VARCHAR(255),
                                     dealcnt                INTEGER,
                                     fin_sec_ru             VARCHAR(255),
                                     fin_sec_en             VARCHAR(255),
                                     fin_sec_kz             VARCHAR(255),
                                     board_ru               VARCHAR(255),
                                     board_en               VARCHAR(255),
                                     board_kz               VARCHAR(255),
                                     repayment_start_date   DATE,
                                     dohod                  NUMERIC(30,6),
                                     dohod_total            NUMERIC(30,6),
                                     monthly_spark_line     TEXT,
                                     month_trand            NUMERIC(30,6),
                                     month_trand_percent    NUMERIC(30,6),
                                     cnt_mm                 INTEGER,
                                     currency_type          VARCHAR(20),
                                     volume_number          NUMERIC(30,6),
                                     volume_release_number  NUMERIC(30,6),
                                     volume                 NUMERIC(30,6),
                                     volume_release         NUMERIC(30,6),
                                     main_market_ru         VARCHAR(255),
                                     main_market_en         VARCHAR(255),
                                     main_market_kz         VARCHAR(255),
                                     dtm                    INTEGER,
                                     ytm                    NUMERIC(30,6),
                                     subcategory_name_ru    VARCHAR(255),
                                     subcategory_name_en    VARCHAR(255),
                                     subcategory_name_kz    VARCHAR(255),
                                     subcategory_shortname_ru VARCHAR(255),
                                     subcategory_shortname_en VARCHAR(255),
                                     subcategory_shortname_kz VARCHAR(255),
                                     scheme                 TEXT,
                                     t_plus                 BOOLEAN,
                                     liquid_class           INTEGER,
                                     PRIMARY KEY (id)

);
