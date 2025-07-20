package kz.nurgissa.kasestockexchangeparser.repositories;

import kz.nurgissa.kasestockexchangeparser.model.entities.SecurityInstrumentEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface SecurityInstrumentRepository extends ReactiveCrudRepository<SecurityInstrumentEntity, Long> {

}
