package kz.nurgissa.kasestockexchangeparser.controller;

import kz.nurgissa.kasestockexchangeparser.model.dtos.AixInstrumentDto;
import kz.nurgissa.kasestockexchangeparser.model.dtos.AixMarketDepthDto;
import kz.nurgissa.kasestockexchangeparser.model.dtos.ArbitrageItemDto;
import kz.nurgissa.kasestockexchangeparser.service.AixService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class AixControllerTest {

    private AixService aixService;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        aixService = Mockito.mock(AixService.class);
        AixController controller = new AixController(aixService);
        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    void getInstruments_shouldReturnList() {
        AixInstrumentDto item = AixInstrumentDto.builder()
                .secCode("KAP")
                .name("Kazatomprom")
                .assetClass("Equity")
                .currency("KZT")
                .lastTrade(BigDecimal.valueOf(18500))
                .percentChange(BigDecimal.valueOf(1.5))
                .build();

        when(aixService.getInstruments(any(), any(), any(), anyInt()))
                .thenReturn(Mono.just(List.of(item)));

        webTestClient.get()
                .uri("/api/v1/aix/instruments?assetClass=Equity&limit=10")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].secCode").isEqualTo("KAP")
                .jsonPath("$[0].assetClass").isEqualTo("Equity")
                .jsonPath("$[0].lastTrade").isEqualTo(18500);
    }

    @Test
    void getMarketDepth_shouldReturnDepthDto() {
        AixMarketDepthDto depth = AixMarketDepthDto.builder()
                .symbol("KAP")
                .isin("KZ1C00001619")
                .bidRows(List.of(
                        AixMarketDepthDto.OrderRowDto.builder()
                                .volume(100L)
                                .price(BigDecimal.valueOf(18400))
                                .build()
                ))
                .offerRows(List.of(
                        AixMarketDepthDto.OrderRowDto.builder()
                                .volume(150L)
                                .price(BigDecimal.valueOf(18500))
                                .build()
                ))
                .build();

        when(aixService.getMarketDepth("KAP")).thenReturn(Mono.just(depth));

        webTestClient.get()
                .uri("/api/v1/aix/depth/KAP")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.symbol").isEqualTo("KAP")
                .jsonPath("$.bidRows[0].price").isEqualTo(18400)
                .jsonPath("$.offerRows[0].price").isEqualTo(18500);
    }

    @Test
    void getArbitrageOpportunities_shouldReturnList() {
        ArbitrageItemDto arb = ArbitrageItemDto.builder()
                .isin("KZ1C00001619")
                .companyName("Kazatomprom")
                .kaseCode("KZAP")
                .kasePrice(BigDecimal.valueOf(18600))
                .aixCode("KAP")
                .aixPrice(BigDecimal.valueOf(18450))
                .currency("KZT")
                .spreadAbs(BigDecimal.valueOf(150))
                .spreadPercent(BigDecimal.valueOf(0.81))
                .cheaperExchange("AIX")
                .recommendation("Выгоднее купить на AIX")
                .build();

        when(aixService.getArbitrageOpportunities()).thenReturn(Mono.just(List.of(arb)));

        webTestClient.get()
                .uri("/api/v1/aix/arbitrage")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].kaseCode").isEqualTo("KZAP")
                .jsonPath("$[0].aixCode").isEqualTo("KAP")
                .jsonPath("$[0].cheaperExchange").isEqualTo("AIX")
                .jsonPath("$[0].spreadPercent").isEqualTo(0.81);
    }

    @Test
    void getArbitrageByTicker_shouldReturnSingle() {
        ArbitrageItemDto arb = ArbitrageItemDto.builder()
                .isin("KZ1C00001619")
                .companyName("Kazatomprom")
                .kaseCode("KZAP")
                .kasePrice(BigDecimal.valueOf(18600))
                .aixCode("KAP")
                .aixPrice(BigDecimal.valueOf(18450))
                .currency("KZT")
                .build();

        when(aixService.getArbitrageByTicker("KZAP")).thenReturn(Mono.just(arb));

        webTestClient.get()
                .uri("/api/v1/aix/arbitrage/KZAP")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.kaseCode").isEqualTo("KZAP")
                .jsonPath("$.aixCode").isEqualTo("KAP");
    }
}
