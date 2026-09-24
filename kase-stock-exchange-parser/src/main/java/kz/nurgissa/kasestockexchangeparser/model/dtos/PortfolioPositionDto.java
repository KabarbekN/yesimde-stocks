package kz.nurgissa.kasestockexchangeparser.model.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioPositionDto {
    private String ticker;
    private String companyName;
    private BigDecimal quantity;
    private BigDecimal buyPrice;
    private BigDecimal currentPrice;
    private String currency;

    private BigDecimal totalCost;              // quantity * buyPrice
    private BigDecimal currentValue;           // quantity * currentPrice
    private BigDecimal unrealizedPnl;          // currentValue - totalCost
    private BigDecimal unrealizedPnlPercent;   // (currentPrice - buyPrice) / buyPrice * 100
    private BigDecimal portfolioSharePercent;  // Доля в портфеле %

    private BigDecimal expectedAnnualDividends; // Прогноз годовых дивидендов на пакет
    private BigDecimal dividendYieldOnCost;     // Div Yield относительно цены покупки
}
