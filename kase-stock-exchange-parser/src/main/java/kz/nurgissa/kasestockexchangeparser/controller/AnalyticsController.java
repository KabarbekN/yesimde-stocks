package kz.nurgissa.kasestockexchangeparser.controller;

import kz.nurgissa.kasestockexchangeparser.model.dtos.MarketSummaryDto;
import kz.nurgissa.kasestockexchangeparser.service.BondAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final BondAnalyticsService bondAnalyticsService;

    @GetMapping("/summary")
    public Mono<MarketSummaryDto> getMarketSummary() {
        return bondAnalyticsService.getMarketSummary();
    }
}
