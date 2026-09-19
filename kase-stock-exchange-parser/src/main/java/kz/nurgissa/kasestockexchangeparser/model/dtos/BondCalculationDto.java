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
public class BondCalculationDto {
    private String ticker;
    private String name;
    private BigDecimal amountInvested;
    private BigDecimal faceValue;
    private BigDecimal pricePercent;
    private BigDecimal pricePerBond;
    private Long bondCount;
    private BigDecimal totalInvested;
    private BigDecimal totalNominal;
    private BigDecimal capitalGain;
    private BigDecimal couponRate;
    private Integer couponFrequencyPerYear; // 1, 2, 4, 12
    private BigDecimal couponPayoutPerPeriod;
    private BigDecimal annualCouponIncome;
    private Integer dtm;
    private Double durationYears;
    private BigDecimal totalCouponsAllTime;
    private BigDecimal grandTotalReturn;
    private BigDecimal roiPercent;
    private String currency;
    private boolean termsPending;
    private String durationFormatted;
}
