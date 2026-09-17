package kz.nurgissa.kasestockexchangeparser.model.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("aix_security_instrument")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AixInstrumentEntity {

    @Id
    @Column("sec_code")
    private String secCode;

    @Column("isin")
    private String isin;

    @Column("issuer")
    private String issuer;

    @Column("short_name")
    private String shortName;

    @Column("instrument")
    private String instrument;

    @Column("segment")
    private String segment;

    @Column("asset_class")
    private String assetClass;

    @Column("security_group")
    private String securityGroup;

    @Column("currency")
    private String currency;

    @Column("state")
    private String state;

    @Column("reference_price")
    private BigDecimal referencePrice;

    @Column("bid_price")
    private BigDecimal bidPrice;

    @Column("bid_qty")
    private Long bidQty;

    @Column("offer_price")
    private BigDecimal offerPrice;

    @Column("offer_qty")
    private Long offerQty;

    @Column("last_trade")
    private BigDecimal lastTrade;

    @Column("previous_close")
    private BigDecimal previousClose;

    @Column("average_weighted_price")
    private BigDecimal averageWeightedPrice;

    @Column("percent_change")
    private BigDecimal percentChange;

    @Column("price_change")
    private BigDecimal priceChange;

    @Column("volume")
    private BigDecimal volume;

    @Column("value")
    private BigDecimal value;

    @Column("number_of_trades")
    private Integer numberOfTrades;

    @Column("nav")
    private BigDecimal nav;

    @Column("nav_currency")
    private String navCurrency;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
