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

@Table("security_price_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityPriceHistoryEntity {

    @Id
    private Long id;

    @Column("security_instrument_id")
    private Long securityInstrumentId;

    private BigDecimal price;

    @Column("close_price")
    private BigDecimal closePrice;

    @Column("best_bid")
    private BigDecimal bestBid;

    @Column("best_offer")
    private BigDecimal bestOffer;

    private BigDecimal spread;

    @Column("spread_percent")
    private BigDecimal spreadPercent;

    private BigDecimal volkzt;

    private BigDecimal volusd;

    private Integer dealcnt;

    private BigDecimal ytm;

    private BigDecimal dohod;

    @Column("recorded_at")
    private LocalDateTime recordedAt;
}
