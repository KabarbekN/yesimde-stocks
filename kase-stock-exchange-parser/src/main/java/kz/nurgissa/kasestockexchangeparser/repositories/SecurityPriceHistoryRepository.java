package kz.nurgissa.kasestockexchangeparser.repositories;

import kz.nurgissa.kasestockexchangeparser.model.entities.SecurityPriceHistoryEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface SecurityPriceHistoryRepository extends ReactiveCrudRepository<SecurityPriceHistoryEntity, Long> {

    @Query("SELECT * FROM security_price_history WHERE security_instrument_id = :instrumentId ORDER BY recorded_at DESC LIMIT :limit")
    Flux<SecurityPriceHistoryEntity> findRecentByInstrumentId(Long instrumentId, int limit);
}
