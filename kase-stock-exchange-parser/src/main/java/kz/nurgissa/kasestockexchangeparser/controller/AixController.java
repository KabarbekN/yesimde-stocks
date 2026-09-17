package kz.nurgissa.kasestockexchangeparser.controller;

import kz.nurgissa.kasestockexchangeparser.model.dtos.AixInstrumentDto;
import kz.nurgissa.kasestockexchangeparser.model.dtos.AixMarketDepthDto;
import kz.nurgissa.kasestockexchangeparser.model.dtos.ArbitrageItemDto;
import kz.nurgissa.kasestockexchangeparser.service.AixService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/aix")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AixController {

    private final AixService aixService;

    @GetMapping("/instruments")
    public Mono<List<AixInstrumentDto>> getInstruments(
            @RequestParam(required = false) String assetClass,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "100") Integer limit
    ) {
        return aixService.getInstruments(assetClass, currency, search, limit);
    }

    @GetMapping("/depth/{symbol}")
    public Mono<AixMarketDepthDto> getMarketDepth(@PathVariable String symbol) {
        return aixService.getMarketDepth(symbol);
    }

    @GetMapping("/arbitrage")
    public Mono<List<ArbitrageItemDto>> getArbitrageOpportunities() {
        return aixService.getArbitrageOpportunities();
    }

    @GetMapping("/arbitrage/{ticker}")
    public Mono<ArbitrageItemDto> getArbitrageByTicker(@PathVariable String ticker) {
        return aixService.getArbitrageByTicker(ticker);
    }

    @PostMapping("/sync")
    public Mono<Void> triggerSync() {
        return aixService.fetchAndSaveAll();
    }
}
