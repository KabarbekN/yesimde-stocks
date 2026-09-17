package kz.nurgissa.kasestockexchangeparser.service;

import kz.nurgissa.kasestockexchangeparser.model.dtos.AixInstrumentDto;
import kz.nurgissa.kasestockexchangeparser.model.dtos.AixMarketDepthDto;
import kz.nurgissa.kasestockexchangeparser.model.dtos.ArbitrageItemDto;
import reactor.core.publisher.Mono;

import java.util.List;

public interface AixService {

    Mono<Void> fetchAndSaveAll();

    Mono<List<AixInstrumentDto>> getInstruments(String assetClass, String currency, String search, Integer limit);

    Mono<AixMarketDepthDto> getMarketDepth(String symbol);

    Mono<List<ArbitrageItemDto>> getArbitrageOpportunities();

    Mono<ArbitrageItemDto> getArbitrageByTicker(String tickerOrIsin);
}
