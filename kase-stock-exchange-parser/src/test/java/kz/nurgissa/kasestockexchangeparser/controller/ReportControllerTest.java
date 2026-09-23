package kz.nurgissa.kasestockexchangeparser.controller;

import kz.nurgissa.kasestockexchangeparser.model.dtos.ReportMarketSnapshotDto;
import kz.nurgissa.kasestockexchangeparser.service.ReportDataAggregatorService;
import kz.nurgissa.kasestockexchangeparser.service.ReportExportService;
import kz.nurgissa.kasestockexchangeparser.service.report.ReportTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ReportControllerTest {

    private ReportExportService exportService;
    private ReportDataAggregatorService aggregatorService;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        exportService = Mockito.mock(ReportExportService.class);
        aggregatorService = Mockito.mock(ReportDataAggregatorService.class);
        ReportController controller = new ReportController(exportService, aggregatorService);
        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    void getSnapshot_shouldReturnSnapshotDto() {
        ReportMarketSnapshotDto snapshot = ReportTestFixtures.createTestSnapshot();
        when(aggregatorService.buildMarketSnapshot(any())).thenReturn(Mono.just(snapshot));

        webTestClient.get()
                .uri("/api/v1/reports/snapshot?amount=26500000")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.macro.baseRate").isEqualTo(16.25)
                .jsonPath("$.battle.capitalAmount").isEqualTo(26500000)
                .jsonPath("$.topStocks[0].code").isEqualTo("KSPI");
    }

    @Test
    void downloadProPdf_shouldReturnPdfByteArray() {
        byte[] dummyPdf = "%PDF-1.4 dummy content".getBytes();
        when(exportService.exportProPdf(any())).thenReturn(Mono.just(dummyPdf));

        webTestClient.get()
                .uri("/api/v1/reports/pro-pdf?amount=10000000")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_PDF)
                .expectBody(byte[].class).isEqualTo(dummyPdf);
    }

    @Test
    void downloadLightPdf_shouldReturnPdfByteArray() {
        byte[] dummyPdf = "%PDF-1.4 light guide".getBytes();
        when(exportService.exportLightPdf(any())).thenReturn(Mono.just(dummyPdf));

        webTestClient.get()
                .uri("/api/v1/reports/light-pdf?amount=5000000")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_PDF)
                .expectBody(byte[].class).isEqualTo(dummyPdf);
    }

    @Test
    void downloadExcel_shouldReturnXlsxByteArray() {
        byte[] dummyExcel = "PK...fake excel bytes".getBytes();
        when(exportService.exportExcel(any())).thenReturn(Mono.just(dummyExcel));

        webTestClient.get()
                .uri("/api/v1/reports/excel")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .expectBody(byte[].class).isEqualTo(dummyExcel);
    }
}
