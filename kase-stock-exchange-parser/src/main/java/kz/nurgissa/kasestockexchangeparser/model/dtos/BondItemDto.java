package kz.nurgissa.kasestockexchangeparser.model.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BondItemDto {
    private Long id;
    private String code;
    private String secType; // "bond" (corporate) or "gsec" (government)
    private String orgCode;
    private String orgNameRu;
    private String orgShortNameRu;

    private BigDecimal price;
    private BigDecimal closePrice;
    private BigDecimal bestBid;
    private BigDecimal bestOffer;
    private BigDecimal spread;
    private BigDecimal spreadPercent;

    private BigDecimal ytm;
    private BigDecimal dohod;
    private Integer dtm;
    private BigDecimal yearsToMaturity;
    private BigDecimal cupon;
    private LocalDate finishDate;
    private String currency;
    private BigDecimal faceValue;

    private BigDecimal volkzt;
    private BigDecimal volusd;
    private Integer dealcnt;

    private String monthlySparkLine;
    private List<BigDecimal> sparklinePoints;

    private Integer marketMakersCount;
    private BigDecimal gSpread;        // Спред доходности над кривой ГЦБ
    private Integer liquidityScore;    // Индекс ликвидности 0-100
    private String liquidityClass;     // "HIGH", "MEDIUM", "LOW", "ILLIQUID"
}
