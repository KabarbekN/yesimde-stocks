package kz.nurgissa.kasestockexchangeparser.model.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DividendSummaryDto {
    private String ticker;
    private String companyName;
    private BigDecimal currentPrice;
    private String currency;
    private BigDecimal trailingTwelveMonthsYieldPercent; // LTM Div Yield
    private BigDecimal totalPaidLastYear;
    private List<DividendItemDto> history;
    private DividendItemDto nextDividend;
}
