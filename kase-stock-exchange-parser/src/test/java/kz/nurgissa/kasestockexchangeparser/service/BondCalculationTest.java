package kz.nurgissa.kasestockexchangeparser.service;

import kz.nurgissa.kasestockexchangeparser.model.dtos.BondCalculationDto;
import kz.nurgissa.kasestockexchangeparser.model.entities.SecurityInstrumentEntity;
import kz.nurgissa.kasestockexchangeparser.model.entities.TickerEntity;
import kz.nurgissa.kasestockexchangeparser.repositories.MarketMakerRepository;
import kz.nurgissa.kasestockexchangeparser.repositories.SecurityInstrumentRepository;
import kz.nurgissa.kasestockexchangeparser.repositories.SecurityPriceHistoryRepository;
import kz.nurgissa.kasestockexchangeparser.repositories.TickerRepository;
import kz.nurgissa.kasestockexchangeparser.service.impl.DefaultBondAnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

class BondCalculationTest {

    private SecurityInstrumentRepository instrumentRepository;
    private TickerRepository tickerRepository;
    private MarketMakerRepository mmRepository;
    private SecurityPriceHistoryRepository historyRepository;
    private DatabaseClient databaseClient;
    private DefaultBondAnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        instrumentRepository = Mockito.mock(SecurityInstrumentRepository.class);
        tickerRepository = Mockito.mock(TickerRepository.class);
        mmRepository = Mockito.mock(MarketMakerRepository.class);
        historyRepository = Mockito.mock(SecurityPriceHistoryRepository.class);
        databaseClient = Mockito.mock(DatabaseClient.class);

        analyticsService = new DefaultBondAnalyticsService(
                databaseClient,
                instrumentRepository,
                tickerRepository,
                mmRepository,
                historyRepository
        );
    }

    @Test
    void calculateBondReturn_shouldCalculateCorrectYieldAndCoupons() {
        SecurityInstrumentEntity instrument = SecurityInstrumentEntity.builder()
                .id(101L)
                .code("KZTKb3")
                .secType("bond")
                .orgNameRu("Казахтелеком")
                .orgShortNameRu("Казахтелеком")
                .price(BigDecimal.valueOf(95.0)) // 95% of nominal (discount)
                .volumeNumber(BigDecimal.valueOf(1000))
                .volume(BigDecimal.valueOf(1000000)) // faceValue = 1000
                .currencyType("KZT")
                .dtm(730) // ~2 years
                .dohod(BigDecimal.valueOf(18.5))
                .build();

        TickerEntity ticker = TickerEntity.builder()
                .securityInstrumentId(101L)
                .nin("KZ2C00003001")
                .cupon(BigDecimal.valueOf(14.0)) // 14% annual coupon
                .currency("KZT")
                .finishDate(LocalDate.now().plusDays(730))
                .settlementSchemes("4 раза в год")
                .build();

        when(instrumentRepository.findByCode(any(String.class))).thenReturn(Mono.just(instrument));
        when(instrumentRepository.findById(any(Long.class))).thenReturn(Mono.just(instrument));
        when(tickerRepository.findById(any(Long.class))).thenReturn(Mono.just(ticker));
        when(historyRepository.findRecentByInstrumentId(any(), anyInt())).thenReturn(Flux.empty());

        DatabaseClient.GenericExecuteSpec spec = Mockito.mock(DatabaseClient.GenericExecuteSpec.class, Mockito.RETURNS_DEEP_STUBS);
        when(databaseClient.sql(any(String.class))).thenReturn(spec);
        when(spec.bind(any(String.class), any())).thenReturn(spec);
        when(spec.map(any(java.util.function.BiFunction.class)).all()).thenReturn(Flux.empty());

        Mono<BondCalculationDto> result = analyticsService.calculateBondReturn("KZTKb3", BigDecimal.valueOf(500_000));

        StepVerifier.create(result)
                .assertNext(dto -> {
                    assertEquals("KZTKb3", dto.getTicker());
                    assertEquals(0, BigDecimal.valueOf(1000).compareTo(dto.getFaceValue()));
                    assertEquals(0, BigDecimal.valueOf(95.0).compareTo(dto.getPricePercent()));
                    assertEquals(0, new BigDecimal("950.00").compareTo(dto.getPricePerBond()));
                    assertEquals(526L, dto.getBondCount()); // 500000 / 950 = 526
                    assertEquals(0, new BigDecimal("499700.00").compareTo(dto.getTotalInvested())); // 526 * 950
                    assertEquals(0, new BigDecimal("526000.00").compareTo(dto.getTotalNominal())); // 526 * 1000
                    assertEquals(0, new BigDecimal("26300.00").compareTo(dto.getCapitalGain())); // 526000 - 499700
                    assertEquals(4, dto.getCouponFrequencyPerYear()); // quarterly
                    assertFalse(dto.isTermsPending());
                    assertTrue(dto.getRoiPercent().compareTo(BigDecimal.ZERO) > 0);
                })
                .verifyComplete();
    }

    @Test
    void calculateBondReturn_shouldRejectStockTicker() {
        SecurityInstrumentEntity stock = SecurityInstrumentEntity.builder()
                .id(202L)
                .code("KSPI")
                .secType("share")
                .orgNameRu("Kaspi.kz")
                .price(BigDecimal.valueOf(55000))
                .build();

        when(instrumentRepository.findByCode("KSPI")).thenReturn(Mono.just(stock));
        when(instrumentRepository.findById(202L)).thenReturn(Mono.just(stock));
        when(tickerRepository.findById(202L)).thenReturn(Mono.empty());
        when(historyRepository.findRecentByInstrumentId(any(), anyInt())).thenReturn(Flux.empty());

        DatabaseClient.GenericExecuteSpec spec = Mockito.mock(DatabaseClient.GenericExecuteSpec.class, Mockito.RETURNS_DEEP_STUBS);
        when(databaseClient.sql(any(String.class))).thenReturn(spec);
        when(spec.bind(any(String.class), any())).thenReturn(spec);
        when(spec.map(any(java.util.function.BiFunction.class)).all()).thenReturn(Flux.empty());

        Mono<BondCalculationDto> result = analyticsService.calculateBondReturn("KSPI", BigDecimal.valueOf(100_000));

        StepVerifier.create(result)
                .expectError(IllegalArgumentException.class)
                .verify();
    }
}
