package kz.nurgissa.kasestockexchangeparser.controller;

import kz.nurgissa.kasestockexchangeparser.model.dtos.StockItemDto;
import kz.nurgissa.kasestockexchangeparser.service.BondAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stocks")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class StockController {

    private final BondAnalyticsService bondAnalyticsService;

    @GetMapping
    public Mono<List<StockItemDto>> getStocks(
            @RequestParam(required = false) List<String> tickers
    ) {
        return bondAnalyticsService.getTopStocks(tickers);
    }

    @GetMapping("/search")
    public Mono<List<StockItemDto>> searchStocks(
            @RequestParam String query,
            @RequestParam(required = false, defaultValue = "10") Integer limit
    ) {
        return bondAnalyticsService.searchStocks(query, limit != null ? limit : 10);
    }
}
