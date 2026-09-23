package kz.nurgissa.kasestockexchangeparser.service;

import kz.nurgissa.kasestockexchangeparser.model.entities.MarketBenchmarkEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Map;

public interface MarketBenchmarkService {

    Mono<Map<String, BigDecimal>> getBenchmarksMap();

    Mono<BigDecimal> getBenchmarkValue(String key, BigDecimal defaultValue);

    Mono<BigDecimal> getBaseRate();

    Mono<BigDecimal> getFlexibleDepositGesv();

    Mono<BigDecimal> getSavingsDepositGesv();

    Mono<BigDecimal> getKrishaAlmaty1RoomPrice();

    Mono<BigDecimal> getKrishaAlmaty1RoomRent();

    Flux<MarketBenchmarkEntity> getAll();

    Mono<MarketBenchmarkEntity> updateBenchmark(String key, BigDecimal value, String source);
}
