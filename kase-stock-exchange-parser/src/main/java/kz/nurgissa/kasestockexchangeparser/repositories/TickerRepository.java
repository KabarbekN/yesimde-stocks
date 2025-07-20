package kz.nurgissa.kasestockexchangeparser.repositories;

import kz.nurgissa.kasestockexchangeparser.model.entities.TickerEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface TickerRepository extends ReactiveCrudRepository<TickerEntity, Long> {
}
