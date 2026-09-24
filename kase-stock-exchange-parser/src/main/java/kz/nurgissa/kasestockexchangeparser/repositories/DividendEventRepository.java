package kz.nurgissa.kasestockexchangeparser.repositories;

import kz.nurgissa.kasestockexchangeparser.model.entities.DividendEventEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Repository
public interface DividendEventRepository extends R2dbcRepository<DividendEventEntity, Long> {

    @Query("SELECT * FROM dividend_event WHERE UPPER(ticker) = UPPER(:ticker) ORDER BY record_date DESC")
    Flux<DividendEventEntity> findAllByTickerOrderByRecordDateDesc(String ticker);

    @Query("SELECT * FROM dividend_event WHERE record_date >= :cutoffDate OR payment_date >= :cutoffDate ORDER BY record_date ASC")
    Flux<DividendEventEntity> findUpcomingDividends(LocalDate cutoffDate);

    @Query("SELECT * FROM dividend_event ORDER BY record_date DESC LIMIT :limit")
    Flux<DividendEventEntity> findLatestDividends(int limit);

    @Query("SELECT * FROM dividend_event WHERE UPPER(ticker) = UPPER(:ticker) AND record_date = :recordDate AND period = :period LIMIT 1")
    Mono<DividendEventEntity> findByTickerAndRecordDateAndPeriod(String ticker, LocalDate recordDate, String period);
}
