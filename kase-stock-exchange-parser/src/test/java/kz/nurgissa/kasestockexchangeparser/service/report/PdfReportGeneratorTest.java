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

        try {
            com.lowagie.text.pdf.PdfReader reader = new com.lowagie.text.pdf.PdfReader(pdfBytes);
            assertThat(reader.getNumberOfPages()).isEqualTo(6);
            java.nio.file.Files.write(java.nio.file.Path.of("target/test_pro_report.pdf"), pdfBytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void generateLightReport_shouldProduceValidPdfDocument() {
        ReportMarketSnapshotDto snapshot = ReportTestFixtures.createTestSnapshot();

        byte[] pdfBytes = lightPdfGenerator.generateLightReport(snapshot);

        assertThat(pdfBytes).isNotNull().isNotEmpty();
        assertThat(pdfBytes.length).isGreaterThan(5000);

        String header = new String(pdfBytes, 0, Math.min(pdfBytes.length, 8), StandardCharsets.US_ASCII);
        assertThat(header).startsWith("%PDF-");

        try {
            com.lowagie.text.pdf.PdfReader reader = new com.lowagie.text.pdf.PdfReader(pdfBytes);
            assertThat(reader.getNumberOfPages()).isEqualTo(4);
            java.nio.file.Files.write(java.nio.file.Path.of("target/test_light_report.pdf"), pdfBytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
