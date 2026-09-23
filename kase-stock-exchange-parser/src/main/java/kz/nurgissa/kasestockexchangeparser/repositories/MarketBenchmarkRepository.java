package kz.nurgissa.kasestockexchangeparser.repositories;

import kz.nurgissa.kasestockexchangeparser.model.entities.MarketBenchmarkEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface MarketBenchmarkRepository extends ReactiveCrudRepository<MarketBenchmarkEntity, Long> {

    Mono<MarketBenchmarkEntity> findByBenchmarkKey(String benchmarkKey);
}
