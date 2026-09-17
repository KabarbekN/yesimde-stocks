package kz.nurgissa.kasestockexchangeparser.repositories;

import kz.nurgissa.kasestockexchangeparser.model.entities.SecurityInstrumentEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface SecurityInstrumentRepository extends ReactiveCrudRepository<SecurityInstrumentEntity, Long> {

    @Query("""
        SELECT * FROM security_instrument s
        WHERE UPPER(TRIM(s.code)) = UPPER(TRIM(:code))
           OR s.id IN (SELECT security_instrument_id FROM ticker WHERE UPPER(TRIM(nin)) = UPPER(TRIM(:code)) OR UPPER(TRIM(nin2)) = UPPER(TRIM(:code)))
        ORDER BY s.id DESC LIMIT 1
    """)
    reactor.core.publisher.Mono<SecurityInstrumentEntity> findByCode(String code);

    reactor.core.publisher.Flux<SecurityInstrumentEntity> findBySecTypeIn(java.util.Collection<String> secTypes);
}
