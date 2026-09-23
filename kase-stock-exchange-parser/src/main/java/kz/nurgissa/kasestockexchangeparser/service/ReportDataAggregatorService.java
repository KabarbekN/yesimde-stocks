package kz.nurgissa.kasestockexchangeparser.service;

import kz.nurgissa.kasestockexchangeparser.model.dtos.ReportMarketSnapshotDto;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface ReportDataAggregatorService {

    /**
     * Builds a comprehensive dynamic snapshot of the entire market across KASE and AIX,
     * calculating all financial models on the fly for the given capital amount.
     *
     * @param customCapital optional user capital amount in KZT (defaults to median real estate price)
     * @return fully populated ReportMarketSnapshotDto
     */
    Mono<ReportMarketSnapshotDto> buildMarketSnapshot(BigDecimal customCapital);
}
