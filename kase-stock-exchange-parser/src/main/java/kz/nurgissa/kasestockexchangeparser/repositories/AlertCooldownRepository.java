package kz.nurgissa.kasestockexchangeparser.repositories;

import kz.nurgissa.kasestockexchangeparser.model.entities.AlertCooldownEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface AlertCooldownRepository extends ReactiveCrudRepository<AlertCooldownEntity, Long> {

    Mono<AlertCooldownEntity> findByAlertTypeAndTicker(String alertType, String ticker);
}
