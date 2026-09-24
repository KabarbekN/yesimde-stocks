package kz.nurgissa.kasestockexchangeparser.controller;

import kz.nurgissa.kasestockexchangeparser.model.dtos.DividendItemDto;
import kz.nurgissa.kasestockexchangeparser.model.dtos.DividendSummaryDto;
import kz.nurgissa.kasestockexchangeparser.service.DividendService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dividends")
@RequiredArgsConstructor
public class DividendController {

    private final DividendService dividendService;

    @GetMapping("/upcoming")
    public Mono<ResponseEntity<List<DividendItemDto>>> getUpcomingDividends() {
        return dividendService.getUpcomingDividends()
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.ok(List.of()));
    }

    @GetMapping("/{ticker}")
    public Mono<ResponseEntity<DividendSummaryDto>> getDividendSummary(@PathVariable String ticker) {
        return dividendService.getDividendSummaryByTicker(ticker)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
