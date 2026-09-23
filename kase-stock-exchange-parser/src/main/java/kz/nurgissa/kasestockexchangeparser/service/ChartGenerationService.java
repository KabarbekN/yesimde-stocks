package kz.nurgissa.kasestockexchangeparser.service;

import kz.nurgissa.kasestockexchangeparser.model.dtos.ReportMarketSnapshotDto;

import java.util.List;

public interface ChartGenerationService {

    byte[] generateTopEquitiesBarChart(List<ReportMarketSnapshotDto.StockReportItemDto> topStocks, boolean darkMode, int width, int height);

    byte[] generateYieldCurveChart(boolean darkMode, int width, int height);

    byte[] generateSectorPieChart(List<ReportMarketSnapshotDto.StockReportItemDto> stocks, boolean darkMode, int width, int height);

    byte[] generateFearAndGreedDial(int score, String label, boolean darkMode, int width, int height);
}
