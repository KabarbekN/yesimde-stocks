package kz.nurgissa.kasestockexchangeparser.controller;

import kz.nurgissa.kasestockexchangeparser.model.dtos.StockItemDto;
import kz.nurgissa.kasestockexchangeparser.service.BondAnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class StockControllerTest {

    private BondAnalyticsService bondAnalyticsService;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        bondAnalyticsService = Mockito.mock(BondAnalyticsService.class);
        StockController controller = new StockController(bondAnalyticsService);
        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    void getStocks_shouldReturnStocksList() {
        StockItemDto stock = StockItemDto.builder()
                .code("KSPI")
                .name("Kaspi.kz")
                .price(BigDecimal.valueOf(55000))
                .change(BigDecimal.valueOf(1500))
                .changePercent(BigDecimal.valueOf(2.8))
                .currency("KZT")
                .build();

        when(bondAnalyticsService.getTopStocks(null)).thenReturn(Mono.just(List.of(stock)));

        webTestClient.get()
                .uri("/api/v1/stocks")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].code").isEqualTo("KSPI")
                .jsonPath("$[0].name").isEqualTo("Kaspi.kz")
                .jsonPath("$[0].price").isEqualTo(55000);
    }

    @Test
    void searchStocks_shouldReturnMatchingStocks() {
        StockItemDto stock = StockItemDto.builder()
                .code("HSBK")
                .name("Halyk Bank")
                .price(BigDecimal.valueOf(380))
                .currency("KZT")
                .build();

        when(bondAnalyticsService.searchStocks(anyString(), anyInt())).thenReturn(Mono.just(List.of(stock)));

        webTestClient.get()
                .uri("/api/v1/stocks/search?query=HSBK&limit=5")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].code").isEqualTo("HSBK")
                .jsonPath("$[0].name").isEqualTo("Halyk Bank");
    }
}
