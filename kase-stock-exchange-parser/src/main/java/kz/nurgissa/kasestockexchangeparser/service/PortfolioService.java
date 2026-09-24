package kz.nurgissa.kasestockexchangeparser.service;

import kz.nurgissa.kasestockexchangeparser.model.dtos.PortfolioPositionDto;
import kz.nurgissa.kasestockexchangeparser.model.dtos.PortfolioSummaryDto;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface PortfolioService {

    /**
     * Get complete portfolio summary and metrics for user chatId.
     */
    Mono<PortfolioSummaryDto> getPortfolioSummary(Long chatId);

    /**
     * Add or update stock/bond holding in user portfolio.
     * If buyPrice is null, defaults to current market price.
     */
    Mono<PortfolioPositionDto> addOrUpdatePosition(Long chatId, String ticker, BigDecimal quantity, BigDecimal buyPrice);

    /**
     * Remove specific ticker from user portfolio.
     */
    Mono<Void> removePosition(Long chatId, String ticker);

    /**
     * Clear all positions for user portfolio.
     */
    Mono<Void> clearPortfolio(Long chatId);
}
