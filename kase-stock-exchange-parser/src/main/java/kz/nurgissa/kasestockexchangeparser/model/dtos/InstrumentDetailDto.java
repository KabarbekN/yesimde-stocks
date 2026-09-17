package kz.nurgissa.kasestockexchangeparser.model.dtos;

import kz.nurgissa.kasestockexchangeparser.model.entities.MarketMakerEntity;
import kz.nurgissa.kasestockexchangeparser.model.entities.SecurityInstrumentEntity;
import kz.nurgissa.kasestockexchangeparser.model.entities.SecurityPriceHistoryEntity;
import kz.nurgissa.kasestockexchangeparser.model.entities.TickerEntity;
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
public class InstrumentDetailDto {
    private SecurityInstrumentEntity instrument;
    private TickerEntity ticker;
    private List<MarketMakerEntity> marketMakers;
    private List<SecurityPriceHistoryEntity> priceHistory;
    private BigDecimal spread;
    private BigDecimal spreadPercent;
    private Integer liquidityScore;
    private List<BigDecimal> sparklinePoints;
    private BigDecimal effectiveYield;
    private BigDecimal yearsToMaturity;
    private BigDecimal faceValue;
    private String resolvedCurrency;
}
