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
public class PortfolioSummaryDto {
    private Long chatId;
    private int totalPositionsCount;
    private BigDecimal totalInvested;                // Сумма инвестиций
    private BigDecimal totalCurrentValue;            // Текущая стоимость портфеля
    private BigDecimal totalUnrealizedPnl;           // Общая прибыль/убыток в KZT
    private BigDecimal totalUnrealizedPnlPercent;    // Общая доходность %
    private BigDecimal totalExpectedAnnualDividends; // Ожидаемые дивиденды за год со всего портфеля
    private BigDecimal portfolioDividendYieldPercent;// Средневзвешенная дивдоходность
    private List<PortfolioPositionDto> positions;
}
