package kz.nurgissa.kasestockexchangeparser.repositories;

import kz.nurgissa.kasestockexchangeparser.model.entities.UserPortfolioEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface UserPortfolioRepository extends R2dbcRepository<UserPortfolioEntity, Long> {

    @Query("SELECT * FROM user_portfolio WHERE chat_id = :chatId ORDER BY ticker ASC")
    Flux<UserPortfolioEntity> findAllByChatIdOrderByTickerAsc(Long chatId);

    @Query("SELECT * FROM user_portfolio WHERE chat_id = :chatId AND UPPER(ticker) = UPPER(:ticker) LIMIT 1")
    Mono<UserPortfolioEntity> findByChatIdAndTicker(Long chatId, String ticker);

    @Modifying
    @Query("DELETE FROM user_portfolio WHERE chat_id = :chatId AND UPPER(ticker) = UPPER(:ticker)")
    Mono<Integer> deleteByChatIdAndTicker(Long chatId, String ticker);

    @Modifying
    @Query("DELETE FROM user_portfolio WHERE chat_id = :chatId")
    Mono<Integer> deleteAllByChatId(Long chatId);
}
