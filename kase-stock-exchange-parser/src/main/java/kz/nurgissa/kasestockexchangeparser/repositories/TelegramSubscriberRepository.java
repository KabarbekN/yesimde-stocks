package kz.nurgissa.kasestockexchangeparser.repositories;

import kz.nurgissa.kasestockexchangeparser.model.entities.TelegramSubscriberEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface TelegramSubscriberRepository extends ReactiveCrudRepository<TelegramSubscriberEntity, Long> {

    Flux<TelegramSubscriberEntity> findAllBySubNewBondsTrue();

    Flux<TelegramSubscriberEntity> findAllBySubDiscountsTrue();

    Flux<TelegramSubscriberEntity> findAllBySubWhalesTrue();

    Flux<TelegramSubscriberEntity> findAllBySubCouponsTrue();

    Flux<TelegramSubscriberEntity> findAllBySubStocksTrue();

    @Modifying
    @Query("""
        INSERT INTO telegram_subscriber (chat_id, username, first_name, sub_new_bonds, sub_discounts, sub_whales, sub_coupons, sub_stocks, watchlist, created_at, updated_at)
        VALUES (:chatId, :username, :firstName, :subNewBonds, :subDiscounts, :subWhales, :subCoupons, :subStocks, :watchlist, :createdAt, :updatedAt)
        ON CONFLICT (chat_id) DO UPDATE SET
            username = EXCLUDED.username,
            first_name = EXCLUDED.first_name,
            updated_at = EXCLUDED.updated_at
    """)
    Mono<Integer> registerOrUpdate(
            Long chatId,
            String username,
            String firstName,
            Boolean subNewBonds,
            Boolean subDiscounts,
            Boolean subWhales,
            Boolean subCoupons,
            Boolean subStocks,
            String watchlist,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    );

    @Modifying
    @Query("""
        UPDATE telegram_subscriber
        SET sub_new_bonds = false,
            sub_discounts = false,
            sub_whales = false,
            sub_coupons = false,
            sub_stocks = false,
            updated_at = :now
        WHERE chat_id = :chatId
    """)
    Mono<Integer> deactivateAllSubscriptions(Long chatId, LocalDateTime now);
}
