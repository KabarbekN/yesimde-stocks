package kz.nurgissa.kasestockexchangeparser.service.impl;

import kz.nurgissa.kasestockexchangeparser.model.entities.MarketBenchmarkEntity;
import kz.nurgissa.kasestockexchangeparser.repositories.MarketBenchmarkRepository;
import kz.nurgissa.kasestockexchangeparser.service.MarketBenchmarkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultMarketBenchmarkService implements MarketBenchmarkService {

    private final MarketBenchmarkRepository benchmarkRepository;

    @Override
    public Mono<Map<String, BigDecimal>> getBenchmarksMap() {
        return benchmarkRepository.findAll()
                .collectMap(MarketBenchmarkEntity::getBenchmarkKey, MarketBenchmarkEntity::getNumericValue)
                .defaultIfEmpty(new HashMap<>());
    }

    @Override
    public Mono<BigDecimal> getBenchmarkValue(String key, BigDecimal defaultValue) {
        return benchmarkRepository.findByBenchmarkKey(key)
                .map(MarketBenchmarkEntity::getNumericValue)
                .defaultIfEmpty(defaultValue);
    }

    @Override
    public Mono<BigDecimal> getBaseRate() {
        return getBenchmarkValue("KZ_BASE_RATE", new BigDecimal("16.25"));
    }

    @Override
    public Mono<BigDecimal> getFlexibleDepositGesv() {
        return getBenchmarkValue("DEPOSIT_FLEXIBLE_GESV", new BigDecimal("14.50"));
    }

    @Override
    public Mono<BigDecimal> getSavingsDepositGesv() {
        return getBenchmarkValue("DEPOSIT_SAVINGS_GESV", new BigDecimal("17.50"));
    }

    @Override
    public Mono<BigDecimal> getKrishaAlmaty1RoomPrice() {
        return getBenchmarkValue("KRISHA_ALMATY_1ROOM_PRICE", new BigDecimal("26500000.00"));
    }

    @Override
    public Mono<BigDecimal> getKrishaAlmaty1RoomRent() {
        return getBenchmarkValue("KRISHA_ALMATY_1ROOM_RENT", new BigDecimal("230000.00"));
    }

    @Override
    public Flux<MarketBenchmarkEntity> getAll() {
        return benchmarkRepository.findAll();
    }

    @Override
    public Mono<MarketBenchmarkEntity> updateBenchmark(String key, BigDecimal value, String source) {
        return benchmarkRepository.findByBenchmarkKey(key)
                .flatMap(existing -> {
                    existing.setNumericValue(value);
                    if (source != null) existing.setSourceName(source);
                    existing.setUpdatedAt(LocalDateTime.now());
                    return benchmarkRepository.save(existing);
                })
                .switchIfEmpty(
                        benchmarkRepository.save(MarketBenchmarkEntity.builder()
                                .benchmarkKey(key)
                                .benchmarkName(key)
                                .numericValue(value)
                                .unit("VALUE")
                                .sourceName(source != null ? source : "MANUAL")
                                .updatedAt(LocalDateTime.now())
                                .build())
                );
    }
}
