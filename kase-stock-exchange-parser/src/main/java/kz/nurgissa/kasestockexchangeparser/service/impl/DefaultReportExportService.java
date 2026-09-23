package kz.nurgissa.kasestockexchangeparser.service.impl;

import kz.nurgissa.kasestockexchangeparser.service.ReportDataAggregatorService;
import kz.nurgissa.kasestockexchangeparser.service.ReportExportService;
import kz.nurgissa.kasestockexchangeparser.service.report.ExcelReportGenerator;
import kz.nurgissa.kasestockexchangeparser.service.report.LightPdfReportGenerator;
import kz.nurgissa.kasestockexchangeparser.service.report.ProPdfReportGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultReportExportService implements ReportExportService {

    private final ReportDataAggregatorService aggregatorService;
    private final ExcelReportGenerator excelGenerator;
    private final ProPdfReportGenerator proPdfGenerator;
    private final LightPdfReportGenerator lightPdfGenerator;

    @Override
    public Mono<byte[]> exportProPdf(BigDecimal capital) {
        return aggregatorService.buildMarketSnapshot(capital)
                .publishOn(Schedulers.boundedElastic())
                .map(proPdfGenerator::generateProReport);
    }

    @Override
    public Mono<byte[]> exportLightPdf(BigDecimal capital) {
        return aggregatorService.buildMarketSnapshot(capital)
                .publishOn(Schedulers.boundedElastic())
                .map(lightPdfGenerator::generateLightReport);
    }

    @Override
    public Mono<byte[]> exportExcel(BigDecimal capital) {
        return aggregatorService.buildMarketSnapshot(capital)
                .publishOn(Schedulers.boundedElastic())
                .map(excelGenerator::generateExcelReport);
    }
}
