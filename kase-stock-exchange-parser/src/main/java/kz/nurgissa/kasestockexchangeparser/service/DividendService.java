package kz.nurgissa.kasestockexchangeparser.service;

import kz.nurgissa.kasestockexchangeparser.model.dtos.DividendItemDto;
import kz.nurgissa.kasestockexchangeparser.model.dtos.DividendSummaryDto;
import kz.nurgissa.kasestockexchangeparser.model.entities.DividendEventEntity;
import reactor.core.publisher.Mono;

import java.util.List;

public interface DividendService {

    /**
     * Get list of upcoming dividend cut-offs and payouts with dynamically calculated yields.
     */
    Mono<List<DividendItemDto>> getUpcomingDividends();

    /**
     * Get dividend history and current metrics for a specific stock ticker.
     */
    Mono<DividendSummaryDto> getDividendSummaryByTicker(String ticker);

    /**
     * Save or update dividend announcement.
     */
    Mono<DividendEventEntity> saveDividendEvent(DividendEventEntity entity);
}
