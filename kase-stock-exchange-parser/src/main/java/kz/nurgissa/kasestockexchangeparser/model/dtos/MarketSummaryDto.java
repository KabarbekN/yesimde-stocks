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
public class MarketSummaryDto {
    private Long totalInstruments;
    private Long totalActiveTraded;
    private BigDecimal totalVolumeKzt;
    private BigDecimal totalVolumeUsd;
    private BigDecimal averageSpreadPercent;

    private List<TopInstrumentDto> topVolume;
    private List<TopInstrumentDto> topYieldBonds;
    private List<TopInstrumentDto> mostLiquid;
    private List<AnomalyItemDto> recentAnomalies;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopInstrumentDto {
        private Long id;
        private String code;
        private String name;
        private String secType;
        private BigDecimal price;
        private BigDecimal ytm;
        private BigDecimal volumeKzt;
        private BigDecimal spreadPercent;
        private BigDecimal trandPercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnomalyItemDto {
        private Long id;
        private String code;
        private String name;
        private String type; // "VOLUME_SPIKE", "TIGHT_SPREAD", "HIGH_YIELD"
        private String description;
        private BigDecimal metricValue;
    }
}
