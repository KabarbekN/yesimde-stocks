package kz.nurgissa.kasestockexchangeparser.service.report;

import kz.nurgissa.kasestockexchangeparser.model.dtos.ReportMarketSnapshotDto;
import kz.nurgissa.kasestockexchangeparser.service.impl.DefaultChartGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PdfReportGeneratorTest {

    private ProPdfReportGenerator proPdfGenerator;
    private LightPdfReportGenerator lightPdfGenerator;

    @BeforeEach
    void setUp() {
        ReportFontProvider fontProvider = new ReportFontProvider();
        DefaultChartGenerationService chartService = new DefaultChartGenerationService();
        proPdfGenerator = new ProPdfReportGenerator(fontProvider, chartService);
        lightPdfGenerator = new LightPdfReportGenerator(fontProvider);
    }

    @Test
    void generateProReport_shouldProduceValidPdfDocument() {
        ReportMarketSnapshotDto snapshot = ReportTestFixtures.createTestSnapshot();

        byte[] pdfBytes = proPdfGenerator.generateProReport(snapshot);

        assertThat(pdfBytes).isNotNull().isNotEmpty();
        assertThat(pdfBytes.length).isGreaterThan(5000);

        // Standard PDF file signature starts with %PDF-
        String header = new String(pdfBytes, 0, Math.min(pdfBytes.length, 8), StandardCharsets.US_ASCII);
        assertThat(header).startsWith("%PDF-");
    }

    @Test
    void generateLightReport_shouldProduceValidPdfDocument() {
        ReportMarketSnapshotDto snapshot = ReportTestFixtures.createTestSnapshot();

        byte[] pdfBytes = lightPdfGenerator.generateLightReport(snapshot);

        assertThat(pdfBytes).isNotNull().isNotEmpty();
        assertThat(pdfBytes.length).isGreaterThan(5000);

        String header = new String(pdfBytes, 0, Math.min(pdfBytes.length, 8), StandardCharsets.US_ASCII);
        assertThat(header).startsWith("%PDF-");
    }
}
