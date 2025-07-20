package kz.nurgissa.kasestockexchangeparser.service.impl;

import kz.nurgissa.kasestockexchangeparser.client.KaseClient;
import kz.nurgissa.kasestockexchangeparser.model.dtos.SecurityInstrumentResponse;
import kz.nurgissa.kasestockexchangeparser.model.entities.InstrumentMarketMakerEntity;
import kz.nurgissa.kasestockexchangeparser.model.entities.MarketMakerEntity;
import kz.nurgissa.kasestockexchangeparser.model.entities.SecurityInstrumentEntity;
import kz.nurgissa.kasestockexchangeparser.model.entities.TickerEntity;
import kz.nurgissa.kasestockexchangeparser.model.mapper.SecurityInstrumentMapper;
import kz.nurgissa.kasestockexchangeparser.repositories.InstrumentMarketMakerRepository;
import kz.nurgissa.kasestockexchangeparser.repositories.MarketMakerRepository;
import kz.nurgissa.kasestockexchangeparser.repositories.SecurityInstrumentRepository;
import kz.nurgissa.kasestockexchangeparser.repositories.TickerRepository;
import kz.nurgissa.kasestockexchangeparser.service.SecurityInstrumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

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

    @Override
    public Mono<Void> saveAll(Flux<SecurityInstrumentResponse> flux) {
        return flux
                .flatMap(dto -> {
                    SecurityInstrumentEntity instr = securityInstrumentMapper.toSecurityInstrumentEntity(dto);
                    TickerEntity ticker = securityInstrumentMapper.toTickerEntity(dto);
                    List<MarketMakerEntity> makers = securityInstrumentMapper.toMarketMakerEntityList(dto);

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

                    return upsertInstr
                            .then(upsertTick)
                            .thenMany(upsertMakers)
                            .then();
                }, PARALLELISM)
                .then();
    }

    @Override
    public Mono<Void> fetchAndSaveAll() {
        return kaseClient.fetchAllSecurityInstruments()
                .transform(this::saveAll)
                .then();
    }

    private Mono<Void> upsertInstrument(SecurityInstrumentEntity instrument) {
        return securityInstrumentRepository.existsById(instrument.getId())
                .flatMap(exists -> exists
                        ? securityInstrumentRepository.save(instrument).then()
                        : template.insert(SecurityInstrumentEntity.class).using(instrument).then()
                )
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
                .flatMap(exists -> exists
                        ? tickerRepository.save(ticker).then()
                        : template.insert(TickerEntity.class).using(ticker).then()
                )
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
