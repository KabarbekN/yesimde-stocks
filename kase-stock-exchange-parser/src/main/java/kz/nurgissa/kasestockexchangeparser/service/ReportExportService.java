package kz.nurgissa.kasestockexchangeparser.service;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface ReportExportService {

    Mono<byte[]> exportProPdf(BigDecimal capital);

    Mono<byte[]> exportLightPdf(BigDecimal capital);

    Mono<byte[]> exportExcel(BigDecimal capital);
}
