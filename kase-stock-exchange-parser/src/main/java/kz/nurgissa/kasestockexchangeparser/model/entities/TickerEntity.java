package kz.nurgissa.kasestockexchangeparser.model.entities;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Table("ticker")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TickerEntity {
    /** PK = FK to SecurityInstrumentEntity.id */
    @Id
    @Column("security_instrument_id")
    private Long securityInstrumentId;

    @Column("type_sector")
    private Integer typeSector;

    @Column("nin")
    private String nin;

    @Column("nin2")
    private String nin2;

    @Column("ofic_list")
    private Boolean oficList;

    @Column("non_list")
    private Boolean nonList;

    @Column("cur_open_trades")
    private LocalDate curOpenTrades;

    @Column("amount")
    private BigDecimal amount;

    @Column("instr_name_ru")
    private String instrNameRu;

    @Column("instr_name_en")
    private String instrNameEn;

    @Column("instr_name_kz")
    private String instrNameKz;

    @Column("nind")
    private String nind;

    @Column("nind2")
    private String nind2;

    @Column("exc_date")
    private LocalDate excDate;

    @Column("spot")
    private Boolean spot;

    @Column("swop")
    private Boolean swop;

    @Column("underlying_ru")
    private String underlyingRu;

    @Column("underlying_en")
    private String underlyingEn;

    @Column("underlying_kz")
    private String underlyingKz;

    @Column("settlement_schemes")
    private String settlementSchemes;

    @Column("typesec_ru")
    private String typesecRu;

    @Column("typesec_en")
    private String typesecEn;

    @Column("typesec_kz")
    private String typesecKz;

    @Column("category_offlist_ru")
    private String categoryOfflistRu;

    @Column("category_offlist_en")
    private String categoryOfflistEn;

    @Column("category_offlist_kz")
    private String categoryOfflistKz;

    @Column("securities_list_ru")
    private String securitiesListRu;

    @Column("securities_list_en")
    private String securitiesListEn;

    @Column("securities_list_kz")
    private String securitiesListKz;

    @Column("currency")
    private String currency;

    @Column("trading_currency")
    private String tradingCurrency;

    @Column("settlement_currency")
    private String settlementCurrency;

    @Column("finish_date")
    private LocalDate finishDate;

    @Column("cupon")
    private BigDecimal cupon;

    @Column("cupon2")
    private BigDecimal cupon2;

    @Column("fin_sec_ru")
    private String finSecRu;

    @Column("fin_sec_en")
    private String finSecEn;

    @Column("fin_sec_kz")
    private String finSecKz;

    @Column("offer_predmet")
    private String offerPredmet;

    @Column("include_date_trade_list")
    private LocalDate includeDateTradeList;

    @Column("bond_type_ru")
    private String bondTypeRu;

    @Column("bond_type_kz")
    private String bondTypeKz;

    @Column("bond_type_en")
    private String bondTypeEn;

    @Column("nbrk_view_ru")
    private String nbrkViewRu;

    @Column("nbrk_view_en")
    private String nbrkViewEn;

    @Column("nbrk_view_kz")
    private String nbrkViewKz;

    @Column("gsec_type_ru")
    private String gsecTypeRu;

    @Column("gsec_type_en")
    private String gsecTypeEn;

    @Column("gsec_type_kz")
    private String gsecTypeKz;

    @Column("is_index_kase")
    private Boolean isIndexKase;

    @Column("is_kase_b")
    private Boolean isKaseB;

    @Column("susp_trddat")    // либо "susp_trd_dat" после RENAME
    private LocalDate suspTrdDat;

    @Column("resum_trddat")   // либо "resum_trd_dat" после RENAME
    private LocalDate resumTrdDat;

    @Column("incl_date")
    private LocalDate inclDate;

    @Column("excl_date")
    private LocalDate exclDate;

    @Column("basis")
    private String basis;

    @Column("ticker_category")
    private String tickerCategory;
}
