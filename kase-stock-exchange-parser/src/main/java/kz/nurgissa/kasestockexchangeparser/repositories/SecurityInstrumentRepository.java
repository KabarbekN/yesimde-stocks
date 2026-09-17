package kz.nurgissa.kasestockexchangeparser.repositories;

import kz.nurgissa.kasestockexchangeparser.model.entities.SecurityInstrumentEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface SecurityInstrumentRepository extends ReactiveCrudRepository<SecurityInstrumentEntity, Long> {

    reactor.core.publisher.Mono<SecurityInstrumentEntity> findByCode(String code);

    reactor.core.publisher.Flux<SecurityInstrumentEntity> findBySecTypeIn(java.util.Collection<String> secTypes);
}
