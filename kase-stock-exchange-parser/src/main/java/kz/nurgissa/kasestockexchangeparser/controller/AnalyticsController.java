package kz.nurgissa.kasestockexchangeparser.controller;

import kz.nurgissa.kasestockexchangeparser.model.dtos.MarketSummaryDto;
import kz.nurgissa.kasestockexchangeparser.model.dtos.TechnicalAnalysisDto;
import kz.nurgissa.kasestockexchangeparser.service.BondAnalyticsService;
import kz.nurgissa.kasestockexchangeparser.service.TechnicalAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AnalyticsController {

    private final BondAnalyticsService bondAnalyticsService;
    private final TechnicalAnalysisService technicalAnalysisService;

    @GetMapping("/summary")
    public Mono<MarketSummaryDto> getMarketSummary() {
        return bondAnalyticsService.getMarketSummary();
    }

    @GetMapping("/ta/{ticker}")
    public Mono<TechnicalAnalysisDto> getTechnicalAnalysis(@PathVariable String ticker) {
        return technicalAnalysisService.analyzeInstrument(ticker);
    }
}
