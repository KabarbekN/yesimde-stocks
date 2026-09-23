package kz.nurgissa.kasestockexchangeparser.model.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportMarketSnapshotDto {
    private LocalDateTime generatedAt;
    private BigDecimal investmentAmount;
    private MacroReportStatsDto macro;
    private AssetBattleComparisonDto battle;
    private List<StockReportItemDto> topStocks;
    private List<StockReportItemDto> allStocks;
    private List<BondReportItemDto> topQuasigovBonds;
    private List<BondReportItemDto> topDiscountBonds;
    private List<BondReportItemDto> allBonds;
    private List<ArbitrageItemDto> arbitragePairs;
    private List<CouponMonthDto> paycheck12Months;
    private Map<String, BigDecimal> benchmarks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MacroReportStatsDto {
        private BigDecimal totalEquitiesTurnoverKzt; // e.g. 2.13 Tln
        private Long totalEquitiesDeals; // e.g. 8.42M
        private BigDecimal top3ConcentrationPct; // e.g. 35.2%
        private int fearAndGreedIndex; // 0..100
        private String fearAndGreedLabel; // e.g. "GREED (Жадность)"
        private BigDecimal baseRate; // 16.25%
        private BigDecimal inflationRate; // 8.6%
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssetBattleComparisonDto {
        private BigDecimal capitalAmount; // e.g. 26,500,000 KZT
        
        // Deposit
        private BigDecimal depositRate; // e.g. 14.5%
        private BigDecimal depositAnnualIncome; // e.g. 3,842,500 KZT
        private String depositNotes;

        // Real estate (Krisha.kz)
        private BigDecimal realEstatePrice; // e.g. 26,500,000 KZT
        private BigDecimal monthlyRent; // e.g. 230,000 KZT
        private BigDecimal realEstateNetYield; // e.g. 8.1%
        private BigDecimal realEstateNetAnnualIncome; // e.g. 2,150,000 KZT
        private String realEstateNotes;

        // KASE Bonds
        private BigDecimal bondYield; // e.g. 17.45%
        private BigDecimal bondAnnualIncome; // e.g. 4,624,250 KZT
        private BigDecimal bondAdvantageOverDeposit; // e.g. +781,750 KZT
        private BigDecimal bondAdvantageOverRealEstateMultiple; // e.g. 2.15x
        private String bondNotes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockReportItemDto {
        private String code;
        private String name;
        private String sector;
        private String exchange; // KASE, AIX, GLOBAL
        private BigDecimal currentPrice;
        private String currency;
        private BigDecimal dayChangePct;
        private BigDecimal cumulativeVolumeKzt;
        private Long cumulativeDeals;
        private BigDecimal monthlyVolumeKzt;
        private Long monthlyDeals;
        private BigDecimal avgDealSizeKzt;
        private BigDecimal freeFloatPct;
        private Boolean isIpoSpo;
        private String participantType;
        private Double rsi14;
        private Double sma20;
        private String trend;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BondReportItemDto {
        private String code;
        private String name;
        private String category; // SOVEREIGN, QUASIGOV, DISCOUNT, COMMERCIAL_BANK, MFO, FOREIGN_CURRENCY
        private String currency;
        private BigDecimal ytm;
        private BigDecimal coupon;
        private String couponFrequency;
        private LocalDate finishDate;
        private String durationFormatted;
        private BigDecimal nominal;
        private BigDecimal currentPrice;
        private BigDecimal discountPct;
        private Boolean isTaxExempt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CouponMonthDto {
        private int monthNumber; // 1..12
        private String monthName; // "Янв", "Фев"...
        private String issuerCode;
        private String issuerName;
        private BigDecimal payoutAmount;
    }
}
