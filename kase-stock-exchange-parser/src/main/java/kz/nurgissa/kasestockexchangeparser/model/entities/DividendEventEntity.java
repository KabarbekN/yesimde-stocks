package kz.nurgissa.kasestockexchangeparser.model.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Table("dividend_event")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DividendEventEntity {

    @Id
    private Long id;

    @Column("ticker")
    private String ticker;

    @Column("isin")
    private String isin;

    @Column("company_name")
    private String companyName;

    @Column("record_date")
    private LocalDate recordDate;

    @Column("payment_date")
    private LocalDate paymentDate;

    @Column("announcement_date")
    private LocalDate announcementDate;

    @Column("amount_per_share")
    private BigDecimal amountPerShare;

    @Column("currency")
    private String currency;

    @Column("period")
    private String period;

    @Column("status")
    private String status; // ANNOUNCED, APPROVED, PAID

    @Column("source_url")
    private String sourceUrl;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
