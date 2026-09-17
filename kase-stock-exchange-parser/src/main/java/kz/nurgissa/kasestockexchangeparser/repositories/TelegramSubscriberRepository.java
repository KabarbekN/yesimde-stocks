package kz.nurgissa.kasestockexchangeparser.repositories;

import kz.nurgissa.kasestockexchangeparser.model.entities.TelegramSubscriberEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface TelegramSubscriberRepository extends ReactiveCrudRepository<TelegramSubscriberEntity, Long> {

    Flux<TelegramSubscriberEntity> findAllBySubNewBondsTrue();

    Flux<TelegramSubscriberEntity> findAllBySubDiscountsTrue();

    Flux<TelegramSubscriberEntity> findAllBySubWhalesTrue();

    Flux<TelegramSubscriberEntity> findAllBySubCouponsTrue();

    Flux<TelegramSubscriberEntity> findAllBySubStocksTrue();
}
