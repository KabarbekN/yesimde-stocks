package kz.nurgissa.kasestockexchangeparser.model.entities;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Table("security_market_turnover")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityMarketTurnoverEntity {

    @Id
    private Long id;

    @Column("code")
    private String code;

    @Column("company_name")
    private String companyName;

    @Column("sector")
    private String sector;

    @Column("period_code")
    private String periodCode; // CUMULATIVE_2022_2026, MONTHLY_2026_08

    @Column("period_start")
    private LocalDate periodStart;

    @Column("period_end")
    private LocalDate periodEnd;

    @Column("deals_count")
    private Long dealsCount;

    @Column("volume_kzt")
    private BigDecimal volumeKzt;

    @Column("free_float_pct")
    private BigDecimal freeFloatPct;

    @Column("is_ipo_spo")
    private Boolean isIpoSpo;

    @Column("avg_deal_size_kzt")
    private BigDecimal avgDealSizeKzt;

    @Column("participant_type")
    private String participantType; // RETAIL_DOMINATED, INSTITUTIONAL_HEAVY, BLOCK_DEALS, BALANCED

    @Column("created_at")
    private LocalDateTime createdAt;
}
