package kz.nurgissa.kasestockexchangeparser.service;

import kz.nurgissa.kasestockexchangeparser.model.entities.SecurityMarketTurnoverEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface KaseHistoricalStatsService {

    /**
     * Imports statistical files from classpath if table is empty or forced.
     */
    Mono<Integer> importStatsIfEmpty();

    /**
     * Force reload stats from bundled Excel files.
     */
    Mono<Integer> reloadStats();

    /**
     * Get cumulative turnover for 2022-2026 ordered by volume descending.
     */
    Flux<SecurityMarketTurnoverEntity> getCumulativeStats();

    /**
     * Get monthly turnover for August 2026.
     */
    Flux<SecurityMarketTurnoverEntity> getMonthlyStats();

    /**
     * Get stats by ticker code.
     */
    Flux<SecurityMarketTurnoverEntity> getStatsByCode(String code);
}
