package kz.nurgissa.kasestockexchangeparser.client;

import kz.nurgissa.kasestockexchangeparser.model.dtos.AixInstrumentDto;
import kz.nurgissa.kasestockexchangeparser.model.dtos.AixMarketDepthDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class AixClient {

    private final WebClient aixWebClient;

    public AixClient(@Qualifier("aixWebClient") WebClient aixWebClient) {
        this.aixWebClient = aixWebClient;
    }

    /**
     * Fetch all 350+ instruments from AIX (Equities, Bonds, Sukuk, Sovereign, etc.)
     */
    public Flux<AixInstrumentDto> fetchAllSecurityInstruments() {
        return aixWebClient.get()
                .uri("/table/mw-main-records?instrument=&search=&listing_before=&listing_after=&listing_between_start=&listing_between_end=")
                .retrieve()
                .bodyToFlux(AixInstrumentDto.class);
    }

    /**
     * Fetch 90+ ETF and ETN funds with NAV values
     */
    public Flux<AixInstrumentDto> fetchEtfRecords() {
        return aixWebClient.get()
                .uri("/table/etf-main-records")
                .retrieve()
                .bodyToFlux(AixInstrumentDto.class);
    }

    /**
     * Fetch live Level-2 Order Book (Market Depth) for a symbol
     */
    public Mono<AixMarketDepthDto> fetchMarketDepth(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return Mono.empty();
        }
        return aixWebClient.get()
                .uri("/marketDepth/{symbol}", symbol.trim().toUpperCase())
                .retrieve()
                .bodyToMono(AixMarketDepthDto.class);
    }

    /**
     * Fetch symbol trading summary (open, high, low, close, volume)
     */
    public Mono<AixInstrumentDto> fetchTradingSummary(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return Mono.empty();
        }
        return aixWebClient.get()
                .uri("/symbol/trading-summary/{symbol}", symbol.trim().toUpperCase())
                .retrieve()
                .bodyToMono(AixInstrumentDto.class);
    }
}
