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
public class TechnicalAnalysisDto {
    private String ticker;
    private String name;
    private String exchange; // KASE, AIX
    private BigDecimal currentPrice;
    private String currency;
    private BigDecimal change;
    private BigDecimal changePercent;
    private BigDecimal dayLow;
    private BigDecimal dayHigh;
    private BigDecimal rsi14;
    private String rsiStatus; // OVERBOUGHT, OVERSOLD, NEUTRAL
    private BigDecimal sma20;
    private BigDecimal priceVsSmaPercent;
    private String trendSignal; // BULLISH, BEARISH, NEUTRAL
    private BigDecimal supportLevel;
    private BigDecimal resistanceLevel;
    private String recommendation;
    private Integer historyPointsCount;
}
