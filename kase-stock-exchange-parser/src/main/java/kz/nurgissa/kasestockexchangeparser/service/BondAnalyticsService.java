package kz.nurgissa.kasestockexchangeparser.service;

import kz.nurgissa.kasestockexchangeparser.model.dtos.*;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

public interface BondAnalyticsService {

    Mono<List<BondItemDto>> getBondScreener(
            String currency,
            Double minYtm,
            Double maxYtm,
            Integer minDtm,
            Integer maxDtm,
            Double maxSpread,
            String preset,
            String search,
            String sortBy,
            String sortDir,
            Integer limit,
            Integer offset
    );

    Mono<YieldCurveResponseDto> getYieldCurve(String currency);

    Mono<MarketSummaryDto> getMarketSummary();

    Mono<InstrumentDetailDto> getInstrumentDetail(Long id);

    Mono<InstrumentDetailDto> getInstrumentDetailByCode(String code);

    Mono<List<BondItemDto>> getDiscountBonds(Double maxPricePercent);

    Mono<List<StockItemDto>> getTopStocks(List<String> specificTickers);

    Mono<List<StockItemDto>> searchStocks(String query, int limit);

    Mono<List<BondItemDto>> getUpcomingCouponBonds();

    Mono<BondCalculationDto> calculateBondReturn(String ticker, BigDecimal amount);
}
