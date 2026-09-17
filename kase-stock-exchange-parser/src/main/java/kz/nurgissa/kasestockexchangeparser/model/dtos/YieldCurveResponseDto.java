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
public class YieldCurveResponseDto {

    private List<CurvePointDto> sovereignCurve;
    private List<CurvePointDto> corporatePoints;
    private BenchmarkRatesDto benchmarks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurvePointDto {
        private Long id;
        private String code;
        private String name;
        private String secType;
        private Integer dtm;
        private Double yearsToMaturity;
        private BigDecimal ytm;
        private BigDecimal gSpread;
        private BigDecimal spreadPercent;
        private BigDecimal volumeKzt;
        private String currency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BenchmarkRatesDto {
        private BigDecimal rate1Year;
        private BigDecimal rate3Year;
        private BigDecimal rate5Year;
        private BigDecimal rate10Year;
        private BigDecimal medianYtm;
        private Integer totalBondsCount;
    }
}
