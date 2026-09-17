package kz.nurgissa.kasestockexchangeparser.repositories;

import kz.nurgissa.kasestockexchangeparser.model.entities.PriceAlertTargetEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PriceAlertTargetRepository extends ReactiveCrudRepository<PriceAlertTargetEntity, Long> {

    @Query("SELECT * FROM price_alert_target WHERE is_triggered = FALSE AND UPPER(ticker) = UPPER(:ticker)")
    Flux<PriceAlertTargetEntity> findAllActiveByTicker(String ticker);

    @Query("SELECT * FROM price_alert_target WHERE chat_id = :chatId AND is_triggered = FALSE ORDER BY created_at DESC")
    Flux<PriceAlertTargetEntity> findAllActiveByChatId(Long chatId);

    @Query("SELECT * FROM price_alert_target WHERE is_triggered = FALSE")
    Flux<PriceAlertTargetEntity> findAllActive();

    @Modifying
    @Query("DELETE FROM price_alert_target WHERE id = :id AND chat_id = :chatId")
    Mono<Integer> deleteByIdAndChatId(Long id, Long chatId);
}
