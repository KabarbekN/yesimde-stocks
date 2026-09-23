package kz.nurgissa.kasestockexchangeparser.repositories;

import kz.nurgissa.kasestockexchangeparser.model.entities.SecurityMarketTurnoverEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface SecurityMarketTurnoverRepository extends ReactiveCrudRepository<SecurityMarketTurnoverEntity, Long> {

    Flux<SecurityMarketTurnoverEntity> findByPeriodCodeOrderByVolumeKztDesc(String periodCode);

    Mono<SecurityMarketTurnoverEntity> findByCodeAndPeriodCode(String code, String periodCode);

    Flux<SecurityMarketTurnoverEntity> findByCode(String code);
}
