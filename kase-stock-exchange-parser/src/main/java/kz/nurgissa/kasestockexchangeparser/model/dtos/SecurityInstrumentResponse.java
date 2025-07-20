package kz.nurgissa.kasestockexchangeparser.model.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SecurityInstrumentResponse {
    private Long id;
    private String code;
    @JsonProperty("sec_type")
    private String secType;
    private BigDecimal price;
    @JsonProperty("close_price")
    private BigDecimal closePrice;
    @JsonProperty("best_bid")
    private BigDecimal bestBid;
    @JsonProperty("best_offer")
    private BigDecimal bestOffer;
    private BigDecimal volkzt;
    private BigDecimal volusd;
    private BigDecimal capit;
    private LocalDateTime date0;
    private BigDecimal trand;
    @JsonProperty("trand_percent")
    private BigDecimal trandPercent;
    @JsonProperty("org_code")
    private String orgCode;
    @JsonProperty("org_name_ru")
    private String orgNameRu;
    @JsonProperty("org_name_en")
    private String orgNameEn;
    @JsonProperty("org_name_kz")
    private String orgNameKz;
    @JsonProperty("org_short_name_ru")
    private String orgShortNameRu;
    @JsonProperty("org_short_name_en")
    private String orgShortNameEn;
    @JsonProperty("org_short_name_kz")
    private String orgShortNameKz;
    private Integer dealcnt;
    @JsonProperty("fin_sec_ru")
    private String finSecRu;
    @JsonProperty("fin_sec_en")
    private String finSecEn;
    @JsonProperty("fin_sec_kz")
    private String finSecKz;
    @JsonProperty("board_ru")
    private String boardRu;
    @JsonProperty("board_en")
    private String boardEn;
    @JsonProperty("board_kz")
    private String boardKz;
    @JsonProperty("repayment_start_date")
    private LocalDate repaymentStartDate;
    private BigDecimal dohod;
    @JsonProperty("dohod_total")
    private BigDecimal dohodTotal;
    @JsonProperty("monthly_spark_line")
    private String monthlySparkLine;
    @JsonProperty("month_trand")
    private BigDecimal monthTrand;
    @JsonProperty("month_trand_percent")
    private BigDecimal monthTrandPercent;
    @JsonProperty("cnt_mm")
    private Integer cntMm;
    @JsonProperty("currency_type")
    private String currencyType;
    @JsonProperty("volume_number")
    private BigDecimal volumeNumber;
    @JsonProperty("volume_release_number")
    private BigDecimal volumeReleaseNumber;
    private BigDecimal volume;
    @JsonProperty("volume_release")
    private BigDecimal volumeRelease;
    @JsonProperty("main_market_ru")
    private String mainMarketRu;
    @JsonProperty("main_market_en")
    private String mainMarketEn;
    @JsonProperty("main_market_kz")
    private String mainMarketKz;
    private Ticker ticker;
    @JsonProperty("market_makers")
    private List<MarketMaker> marketMakers;
    private Integer dtm;
    private BigDecimal ytm;
    @JsonProperty("subcategory_name_ru")
    private String subcategoryNameRu;
    @JsonProperty("subcategory_name_en")
    private String subcategoryNameEn;
    @JsonProperty("subcategory_name_kz")
    private String subcategoryNameKz;
    @JsonProperty("subcategory_shortname_ru")
    private String subcategoryShortnameRu;
    @JsonProperty("subcategory_shortname_en")
    private String subcategoryShortnameEn;
    @JsonProperty("subcategory_shortname_kz")
    private String subcategoryShortnameKz;
    private String scheme;
    @JsonProperty("t_plus")
    private Boolean tPlus;
    @JsonProperty("liquid_class")
    private Integer liquidClass;

    @Data
    public static class Ticker {
        @JsonProperty("type_sector")
        private Integer typeSector;
        private String nin;
        private String nin2;
        @JsonProperty("ofic_list")
        private Boolean oficList;
        @JsonProperty("non_list")
        private Boolean nonList;
        @JsonProperty("cur_open_trades")
        private LocalDate curOpenTrades;
        private BigDecimal amount;
        @JsonProperty("instrname_ru")
        private String instrNameRu;
        @JsonProperty("instrname_en")
        private String instrNameEn;
        @JsonProperty("instrname_kz")
        private String instrNameKz;
        private String nind;
        private String nind2;
        @JsonProperty("exc_date")
        private LocalDate excDate;
        private Boolean spot;
        private Boolean swop;
        @JsonProperty("underlying_ru")
        private String underlyingRu;
        @JsonProperty("underlying_en")
        private String underlyingEn;
        @JsonProperty("underlying_kz")
        private String underlyingKz;
        @JsonProperty("settlement_schemes")
        private String settlementSchemes;
        @JsonProperty("typesec_ru")
        private String typesecRu;
        @JsonProperty("typesec_en")
        private String typesecEn;
        @JsonProperty("typesec_kz")
        private String typesecKz;
        @JsonProperty("category_offlist_ru")
        private String categoryOfflistRu;
        @JsonProperty("category_offlist_en")
        private String categoryOfflistEn;
        @JsonProperty("category_offlist_kz")
        private String categoryOfflistKz;
        @JsonProperty("securities_list_ru")
        private String securitiesListRu;
        @JsonProperty("securities_list_en")
        private String securitiesListEn;
        @JsonProperty("securities_list_kz")
        private String securitiesListKz;
        private String currency;
        @JsonProperty("trading_currency")
        private String tradingCurrency;
        @JsonProperty("settlement_currency")
        private String settlementCurrency;
        @JsonProperty("finish_date")
        private LocalDate finishDate;
        private BigDecimal cupon;
        private BigDecimal cupon2;
        @JsonProperty("fin_sec_ru")
        private String finSecRu;
        @JsonProperty("fin_sec_en")
        private String finSecEn;
        @JsonProperty("fin_sec_kz")
        private String finSecKz;
        @JsonProperty("offer_predmet")
        private String offerPredmet;
        @JsonProperty("include_date_trade_list")
        private LocalDate includeDateTradeList;
        @JsonProperty("bond_type_ru")
        private String bondTypeRu;
        @JsonProperty("bond_type_kz")
        private String bondTypeKz;
        @JsonProperty("bond_type_en")
        private String bondTypeEn;
        @JsonProperty("open_trade_date")
        private LocalDate includeTradeDate;
        @JsonProperty("nbrk_view_ru")
        private String nbrkViewRu;
        @JsonProperty("nbrk_view_en")
        private String nbrkViewEn;
        @JsonProperty("nbrk_view_kz")
        private String nbrkViewKz;
        @JsonProperty("gsec_type_ru")
        private String gsecTypeRu;
        @JsonProperty("gsec_type_en")
        private String gsecTypeEn;
        @JsonProperty("gsec_type_kz")
        private String gsecTypeKz;
        @JsonProperty("is_index_kase")
        private Boolean isIndexKase;
        @JsonProperty("is_kase_b")
        private Boolean isKaseB;
        @JsonProperty("susp_trddat")
        private LocalDate suspTrdDat;
        @JsonProperty("resum_trddat")
        private LocalDate resumTrdDat;
        @JsonProperty("incl_date")
        private LocalDate inclDate;
        @JsonProperty("excl_date")
        private LocalDate exclDate;
        private String basis;
        @JsonProperty("ticker_category")
        private String tickerCategory;
    }

    @Data
    public static class MarketMaker {
        @JsonProperty("org_code")
        private String orgCode;
        @JsonProperty("org_name_ru")
        private String orgNameRu;
        @JsonProperty("org_name_en")
        private String orgNameEn;
        @JsonProperty("org_name_kz")
        private String orgNameKz;
        @JsonProperty("org_short_name_ru")
        private String orgShortNameRu;
        @JsonProperty("org_short_name_en")
        private String orgShortNameEn;
        @JsonProperty("org_short_name_kz")
        private String orgShortNameKz;
        @JsonProperty("org_shortest_name_ru")
        private String orgShortestNameRu;
        @JsonProperty("org_shortest_name_en")
        private String orgShortestNameEn;
        @JsonProperty("org_shortest_name_kz")
        private String orgShortestNameKz;
    }
}
