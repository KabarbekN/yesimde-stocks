package kz.nurgissa.kasestockexchangeparser.model.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DividendItemDto {
    private Long id;
    private String ticker;
    private String isin;
    private String companyName;
    private LocalDate recordDate;
    private LocalDate paymentDate;
    private LocalDate announcementDate;
    private BigDecimal amountPerShare;
    private String currency;
    private String period;
    private String status; // ANNOUNCED, APPROVED, PAID
    private String sourceUrl;

    // Dynamically computed metrics against current stock price
    private BigDecimal currentStockPrice;
    private BigDecimal dividendYieldPercent;
    private Long daysUntilRecordDate;
}
