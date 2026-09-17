package kz.nurgissa.kasestockexchangeparser.service;

import kz.nurgissa.kasestockexchangeparser.model.dtos.TechnicalAnalysisDto;
import reactor.core.publisher.Mono;

import java.util.List;

public interface TechnicalAnalysisService {

    Mono<TechnicalAnalysisDto> analyzeInstrument(String ticker);

    Mono<List<TechnicalAnalysisDto>> getMarketPulse(List<String> tickers);
}
