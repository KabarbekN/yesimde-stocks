package kz.nurgissa.kasestockexchangeparser.repositories;

import kz.nurgissa.kasestockexchangeparser.model.entities.InstrumentMarketMakerEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface InstrumentMarketMakerRepository extends ReactiveCrudRepository<InstrumentMarketMakerEntity, Void> {
    @Query("SELECT EXISTS(SELECT 1 FROM instrument_market_maker WHERE security_instrument_id = :instrId AND org_code = :orgCode)")
    Mono<Boolean> existsBySecurityInstrumentIdAndOrgCode(Long instrId, String orgCode);

}
