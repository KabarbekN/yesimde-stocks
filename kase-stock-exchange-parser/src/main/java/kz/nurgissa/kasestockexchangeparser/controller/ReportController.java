package kz.nurgissa.kasestockexchangeparser.controller;

import kz.nurgissa.kasestockexchangeparser.model.dtos.ReportMarketSnapshotDto;
import kz.nurgissa.kasestockexchangeparser.service.ReportDataAggregatorService;
import kz.nurgissa.kasestockexchangeparser.service.ReportExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Slf4j
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ReportController {

    private final ReportExportService exportService;
    private final ReportDataAggregatorService aggregatorService;

    @GetMapping("/snapshot")
    public Mono<ResponseEntity<ReportMarketSnapshotDto>> getSnapshot(
            @RequestParam(required = false) BigDecimal amount
    ) {
        return aggregatorService.buildMarketSnapshot(amount)
                .map(ResponseEntity::ok);
    }

    @GetMapping(value = "/pro-pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public Mono<ResponseEntity<ByteArrayResource>> downloadProPdf(
            @RequestParam(required = false) BigDecimal amount
    ) {
        return exportService.exportProPdf(amount)
                .map(bytes -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"KASE_AIX_Pro_Market_Report.pdf\"")
                        .contentType(MediaType.APPLICATION_PDF)
                        .contentLength(bytes.length)
                        .body(new ByteArrayResource(bytes)));
    }

    @GetMapping(value = "/light-pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public Mono<ResponseEntity<ByteArrayResource>> downloadLightPdf(
            @RequestParam(required = false) BigDecimal amount
    ) {
        return exportService.exportLightPdf(amount)
                .map(bytes -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"KASE_AIX_Investor_Guide.pdf\"")
                        .contentType(MediaType.APPLICATION_PDF)
                        .contentLength(bytes.length)
                        .body(new ByteArrayResource(bytes)));
    }

    @GetMapping(value = "/excel", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Mono<ResponseEntity<ByteArrayResource>> downloadExcel(
            @RequestParam(required = false) BigDecimal amount
    ) {
        return exportService.exportExcel(amount)
                .map(bytes -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"KASE_AIX_Full_Market_Data.xlsx\"")
                        .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                        .contentLength(bytes.length)
                        .body(new ByteArrayResource(bytes)));
    }
}
