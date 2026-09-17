package kz.nurgissa.kasestockexchangeparser.service;

import kz.nurgissa.kasestockexchangeparser.client.AixClient;
import kz.nurgissa.kasestockexchangeparser.model.dtos.AixMarketDepthDto;
import kz.nurgissa.kasestockexchangeparser.repositories.AixSecurityInstrumentRepository;
import kz.nurgissa.kasestockexchangeparser.service.impl.DefaultAixService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

class DefaultAixServiceTest {

    private AixClient aixClient;
    private AixSecurityInstrumentRepository repository;
    private DatabaseClient databaseClient;
    private R2dbcEntityTemplate template;
    private DefaultAixService service;

    @BeforeEach
    void setUp() {
        aixClient = Mockito.mock(AixClient.class);
        repository = Mockito.mock(AixSecurityInstrumentRepository.class);
        databaseClient = Mockito.mock(DatabaseClient.class);
        template = Mockito.mock(R2dbcEntityTemplate.class);
        service = new DefaultAixService(aixClient, repository, databaseClient, template);
    }

    @Test
    void getMarketDepth_shouldDelegateToAixClient() {
        AixMarketDepthDto depth = AixMarketDepthDto.builder()
                .symbol("KAP")
                .isin("KZ1C00001619")
                .bidRows(List.of(
                        AixMarketDepthDto.OrderRowDto.builder().price(BigDecimal.valueOf(18400)).volume(50L).build()
                ))
                .offerRows(List.of(
                        AixMarketDepthDto.OrderRowDto.builder().price(BigDecimal.valueOf(18500)).volume(70L).build()
                ))
                .build();

        when(aixClient.fetchMarketDepth("KAP")).thenReturn(Mono.just(depth));

        StepVerifier.create(service.getMarketDepth("KAP"))
                .assertNext(res -> {
                    assertNotNull(res);
                    assertEquals("KAP", res.getSymbol());
                    assertEquals(1, res.getBidRows().size());
                    assertEquals(1, res.getOfferRows().size());
                })
                .verifyComplete();
    }
}
