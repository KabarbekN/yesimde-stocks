package kz.nurgissa.kasestockexchangeparser.repositories;

import kz.nurgissa.kasestockexchangeparser.model.entities.MarketMakerEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface MarketMakerRepository extends ReactiveCrudRepository<MarketMakerEntity, String> {
}
