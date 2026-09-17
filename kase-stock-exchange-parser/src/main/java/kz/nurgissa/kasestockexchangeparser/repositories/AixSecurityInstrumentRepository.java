package kz.nurgissa.kasestockexchangeparser.repositories;

import kz.nurgissa.kasestockexchangeparser.model.entities.AixInstrumentEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AixSecurityInstrumentRepository extends ReactiveCrudRepository<AixInstrumentEntity, String> {

    Mono<AixInstrumentEntity> findBySecCode(String secCode);

    Flux<AixInstrumentEntity> findAllByIsin(String isin);

    Flux<AixInstrumentEntity> findAllByAssetClass(String assetClass);

    Flux<AixInstrumentEntity> findAllByCurrency(String currency);
}
