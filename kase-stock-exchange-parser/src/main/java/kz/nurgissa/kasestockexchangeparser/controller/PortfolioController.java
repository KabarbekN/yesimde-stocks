package kz.nurgissa.kasestockexchangeparser.controller;

import kz.nurgissa.kasestockexchangeparser.model.dtos.PortfolioPositionDto;
import kz.nurgissa.kasestockexchangeparser.model.dtos.PortfolioSummaryDto;
import kz.nurgissa.kasestockexchangeparser.service.PortfolioService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;

    @GetMapping("/{chatId}")
    public Mono<ResponseEntity<PortfolioSummaryDto>> getPortfolio(@PathVariable Long chatId) {
        return portfolioService.getPortfolioSummary(chatId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/{chatId}/positions")
    public Mono<ResponseEntity<PortfolioPositionDto>> addOrUpdatePosition(
            @PathVariable Long chatId,
            @RequestBody AddPositionRequest request
    ) {
        return portfolioService.addOrUpdatePosition(chatId, request.getTicker(), request.getQuantity(), request.getBuyPrice())
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{chatId}/positions/{ticker}")
    public Mono<ResponseEntity<Void>> removePosition(
            @PathVariable Long chatId,
            @PathVariable String ticker
    ) {
        return portfolioService.removePosition(chatId, ticker)
                .thenReturn(ResponseEntity.noContent().build());
    }

    @DeleteMapping("/{chatId}")
    public Mono<ResponseEntity<Void>> clearPortfolio(@PathVariable Long chatId) {
        return portfolioService.clearPortfolio(chatId)
                .thenReturn(ResponseEntity.noContent().build());
    }

    @Data
    public static class AddPositionRequest {
        private String ticker;
        private BigDecimal quantity;
        private BigDecimal buyPrice;
    }
}
