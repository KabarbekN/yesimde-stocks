package kz.nurgissa.kasestockexchangeparser.service.impl;

import kz.nurgissa.kasestockexchangeparser.client.KaseClient;
import kz.nurgissa.kasestockexchangeparser.model.dtos.SecurityInstrumentResponse;
import kz.nurgissa.kasestockexchangeparser.model.entities.*;
import kz.nurgissa.kasestockexchangeparser.model.mapper.SecurityInstrumentMapper;
import kz.nurgissa.kasestockexchangeparser.repositories.*;
import kz.nurgissa.kasestockexchangeparser.service.SecurityInstrumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultSecurityInstrumentService implements SecurityInstrumentService {

    private static final int PARALLELISM = 64;

    private final MarketMakerRepository marketMakerRepository;
    private final SecurityInstrumentRepository securityInstrumentRepository;
    private final TickerRepository tickerRepository;
    private final SecurityInstrumentMapper securityInstrumentMapper;
    private final KaseClient kaseClient;
    private final R2dbcEntityTemplate template;
    private final InstrumentMarketMakerRepository immRepository;
    private final SecurityPriceHistoryRepository priceHistoryRepository;
    private final kz.nurgissa.kasestockexchangeparser.telegram.TelegramAlertDispatcherService alertDispatcherService;

    private final ConcurrentHashMap<Long, PriceSnapshot> lastRecordedSnapshots = new ConcurrentHashMap<>();

    private record PriceSnapshot(BigDecimal price, BigDecimal bid, BigDecimal offer, BigDecimal volKzt, LocalDateTime timestamp) {}

    @Override
    public Mono<Void> saveAll(Flux<SecurityInstrumentResponse> flux) {
        return flux
                .flatMap(dto -> {
                    SecurityInstrumentEntity instr = securityInstrumentMapper.toSecurityInstrumentEntity(dto);
                    TickerEntity ticker = securityInstrumentMapper.toTickerEntity(dto);
                    List<MarketMakerEntity> makers = securityInstrumentMapper.toMarketMakerEntityList(dto);
                    SecurityPriceHistoryEntity history = securityInstrumentMapper.toPriceHistoryEntity(dto);

                    Mono<Void> upsertInstr = upsertInstrument(instr);
                    Mono<Void> upsertTick  = ticker == null
                            ? Mono.empty()
                            : upsertTicker(ticker);

                    Flux<Void> upsertMakers = Flux.fromIterable(makers)
                            .filter(mm -> mm.getOrgCode() != null && instr.getId() != null)
                            .flatMap(mm ->
                                            upsertMarketMaker(mm)
                                                    .then(upsertInstrumentMarketMakerLink(instr.getId(), mm.getOrgCode())),
                                    PARALLELISM
                            );

                    Mono<Void> saveHistory = (history == null || !shouldRecordPriceHistory(history))
                            ? Mono.empty()
                            : priceHistoryRepository.save(history).then();

                    return upsertInstr
                            .then(upsertTick)
                            .thenMany(upsertMakers)
                            .then(saveHistory);
                }, PARALLELISM)
                .then();
    }

    private boolean shouldRecordPriceHistory(SecurityPriceHistoryEntity history) {
        if (history == null || history.getSecurityInstrumentId() == null) {
            return false;
        }
        Long id = history.getSecurityInstrumentId();
        PriceSnapshot last = lastRecordedSnapshots.get(id);
        if (last == null) {
            lastRecordedSnapshots.put(id, new PriceSnapshot(history.getPrice(), history.getBestBid(), history.getBestOffer(), history.getVolkzt(), LocalDateTime.now()));
            return true;
        }

        boolean priceChanged = !Objects.equals(last.price(), history.getPrice());
        boolean bidChanged = !Objects.equals(last.bid(), history.getBestBid());
        boolean offerChanged = !Objects.equals(last.offer(), history.getBestOffer());
        boolean volumeChanged = !Objects.equals(last.volKzt(), history.getVolkzt());

        if (priceChanged || bidChanged || offerChanged || volumeChanged) {
            lastRecordedSnapshots.put(id, new PriceSnapshot(history.getPrice(), history.getBestBid(), history.getBestOffer(), history.getVolkzt(), LocalDateTime.now()));
            return true;
        }

        // Heartbeat: save at least once every 60 minutes even if price hasn't moved
        if (ChronoUnit.MINUTES.between(last.timestamp(), LocalDateTime.now()) >= 60) {
            lastRecordedSnapshots.put(id, new PriceSnapshot(history.getPrice(), history.getBestBid(), history.getBestOffer(), history.getVolkzt(), LocalDateTime.now()));
            return true;
        }

        return false;
    }

    @Scheduled(cron = "0 0 3 * * ?", zone = "Asia/Almaty")
    public void purgeOldPriceHistory() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
        priceHistoryRepository.deleteOlderThan(cutoff)
                .doOnSuccess(deleted -> log.info("Purged {} historical price records older than 90 days (cutoff: {})", deleted, cutoff))
                .doOnError(e -> log.error("Failed to purge old price history: {}", e.getMessage()))
                .subscribe();
    }

    @Override
    public Mono<Void> fetchAndSaveAll() {
        return kaseClient.fetchAllSecurityInstruments()
                .transform(this::saveAll)
                .then();
    }

    private Mono<Void> upsertInstrument(SecurityInstrumentEntity instrument) {
        return securityInstrumentRepository.existsById(instrument.getId())
                .flatMap(exists -> {
                    if (exists) {
                        return securityInstrumentRepository.save(instrument).then();
                    } else {
                        Mono<Void> insert = template.insert(SecurityInstrumentEntity.class).using(instrument).then();
                        if (("bond".equalsIgnoreCase(instrument.getSecType()) || "gsec".equalsIgnoreCase(instrument.getSecType()))
                                && alertDispatcherService.isEnabled()) {
                            return insert.then(
                                    tickerRepository.findById(instrument.getId())
                                            .flatMap(t -> alertDispatcherService.broadcastNewBondAlert(instrument, t))
                                            .switchIfEmpty(alertDispatcherService.broadcastNewBondAlert(instrument, null))
                            );
                        }
                        return insert;
                    }
                })
                .onErrorResume(DuplicateKeyException.class, ex -> Mono.empty());
    }

    private Mono<Void> upsertMarketMaker(MarketMakerEntity maker) {
        return marketMakerRepository.existsById(maker.getOrgCode())
                .flatMap(exists -> exists
                        ? marketMakerRepository.save(maker).then()
                        : template.insert(MarketMakerEntity.class).using(maker).then()
                )
                .onErrorResume(DuplicateKeyException.class, ex -> Mono.empty());
    }

    private Mono<Void> upsertTicker(TickerEntity ticker) {
        return tickerRepository.existsById(ticker.getSecurityInstrumentId())
                .flatMap(exists -> {
                    if (exists) {
                        return tickerRepository.findById(ticker.getSecurityInstrumentId())
                                .flatMap(oldTicker -> {
                                    boolean hadNoCoupon = (oldTicker.getCupon() == null || oldTicker.getCupon().compareTo(BigDecimal.ZERO) == 0)
                                            && (oldTicker.getCupon2() == null || oldTicker.getCupon2().compareTo(BigDecimal.ZERO) == 0);
                                    boolean hasCouponNow = (ticker.getCupon() != null && ticker.getCupon().compareTo(BigDecimal.ZERO) > 0)
                                            || (ticker.getCupon2() != null && ticker.getCupon2().compareTo(BigDecimal.ZERO) > 0);

                                    return tickerRepository.save(ticker)
                                            .flatMap(savedTicker -> {
                                                if (hadNoCoupon && hasCouponNow && alertDispatcherService.isEnabled()) {
                                                    return securityInstrumentRepository.findById(ticker.getSecurityInstrumentId())
                                                            .flatMap(instr -> alertDispatcherService.broadcastBondTermsUpdatedAlert(instr, savedTicker))
                                                            .thenReturn(savedTicker);
                                                }
                                                return Mono.just(savedTicker);
                                            })
                                            .then();
                                });
                    } else {
                        return template.insert(TickerEntity.class).using(ticker)
                                .flatMap(savedTicker -> {
                                    boolean hasCoupon = (savedTicker.getCupon() != null && savedTicker.getCupon().compareTo(BigDecimal.ZERO) > 0)
                                            || (savedTicker.getCupon2() != null && savedTicker.getCupon2().compareTo(BigDecimal.ZERO) > 0);
                                    if (hasCoupon && alertDispatcherService.isEnabled()) {
                                        return securityInstrumentRepository.findById(savedTicker.getSecurityInstrumentId())
                                                .flatMap(instr -> alertDispatcherService.broadcastBondTermsUpdatedAlert(instr, savedTicker))
                                                .thenReturn(savedTicker);
                                    }
                                    return Mono.just(savedTicker);
                                })
                                .then();
                    }
                })
                .onErrorResume(DuplicateKeyException.class, ex -> Mono.empty());
    }

    private Mono<Void> upsertInstrumentMarketMakerLink(Long instrId, String orgCode) {
        return immRepository.existsBySecurityInstrumentIdAndOrgCode(instrId, orgCode)
                .flatMap(exists -> exists
                        ? Mono.empty()
                        : immRepository.save(new InstrumentMarketMakerEntity(instrId, orgCode)).then()
                )
                .onErrorResume(DuplicateKeyException.class, ex -> Mono.empty());
    }
}
