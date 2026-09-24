package kz.nurgissa.kasestockexchangeparser.service;

import kz.nurgissa.kasestockexchangeparser.model.dtos.DividendItemDto;
import kz.nurgissa.kasestockexchangeparser.model.dtos.DividendSummaryDto;
import kz.nurgissa.kasestockexchangeparser.model.entities.DividendEventEntity;
import kz.nurgissa.kasestockexchangeparser.model.entities.SecurityInstrumentEntity;
import kz.nurgissa.kasestockexchangeparser.repositories.DividendEventRepository;
import kz.nurgissa.kasestockexchangeparser.repositories.SecurityInstrumentRepository;
import kz.nurgissa.kasestockexchangeparser.service.impl.DefaultDividendService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultDividendServiceTest {

    @Mock
    private DividendEventRepository dividendEventRepository;

    @Mock
    private SecurityInstrumentRepository securityInstrumentRepository;

    private DefaultDividendService dividendService;

    @BeforeEach
    void setUp() {
        dividendService = new DefaultDividendService(dividendEventRepository, securityInstrumentRepository);
    }

    @Test
    void getUpcomingDividends_shouldReturnEnrichedDividends() {
        DividendEventEntity entity = DividendEventEntity.builder()
                .id(1L)
                .ticker("HSBK")
                .companyName("Halyk Bank")
                .amountPerShare(BigDecimal.valueOf(28.0))
                .currency("KZT")
                .recordDate(LocalDate.now().plusDays(30))
                .status("APPROVED")
                .period("2025 год")
                .build();

        SecurityInstrumentEntity stock = SecurityInstrumentEntity.builder()
                .id(100L)
                .code("HSBK")
                .price(BigDecimal.valueOf(280.0))
                .currencyType("KZT")
                .build();

        when(dividendEventRepository.findUpcomingDividends(any(LocalDate.class)))
                .thenReturn(Flux.just(entity));
        when(securityInstrumentRepository.findByCode(eq("HSBK")))
                .thenReturn(Mono.just(stock));

        StepVerifier.create(dividendService.getUpcomingDividends())
                .assertNext(list -> {
                    assertThat(list).hasSize(1);
                    DividendItemDto dto = list.get(0);
                    assertThat(dto.getTicker()).isEqualTo("HSBK");
                    assertThat(dto.getAmountPerShare()).isEqualByComparingTo(BigDecimal.valueOf(28.0));
                    assertThat(dto.getCurrentStockPrice()).isEqualByComparingTo(BigDecimal.valueOf(280.0));
                    // 28 / 280 * 100 = 10.00%
                    assertThat(dto.getDividendYieldPercent()).isEqualByComparingTo(BigDecimal.valueOf(10.0));
                    assertThat(dto.getDaysUntilRecordDate()).isPositive();
                })
                .verifyComplete();
    }

    @Test
    void getDividendSummaryByTicker_shouldCalculateLtmYieldAndNextPayout() {
        DividendEventEntity pastDiv = DividendEventEntity.builder()
                .id(1L)
                .ticker("KSPI")
                .companyName("Kaspi.kz")
                .amountPerShare(BigDecimal.valueOf(850.0))
                .currency("KZT")
                .recordDate(LocalDate.now().minusMonths(3))
                .status("PAID")
                .period("Q1 2026")
                .build();

        DividendEventEntity nextDiv = DividendEventEntity.builder()
                .id(2L)
                .ticker("KSPI")
                .companyName("Kaspi.kz")
                .amountPerShare(BigDecimal.valueOf(850.0))
                .currency("KZT")
                .recordDate(LocalDate.now().plusMonths(1))
                .status("ANNOUNCED")
                .period("Q2 2026")
                .build();

        SecurityInstrumentEntity stock = SecurityInstrumentEntity.builder()
                .id(200L)
                .code("KSPI")
                .price(BigDecimal.valueOf(17000.0))
                .currencyType("KZT")
                .build();

        when(securityInstrumentRepository.findByCode(eq("KSPI")))
                .thenReturn(Mono.just(stock));
        when(dividendEventRepository.findAllByTickerOrderByRecordDateDesc(eq("KSPI")))
                .thenReturn(Flux.just(nextDiv, pastDiv));

        StepVerifier.create(dividendService.getDividendSummaryByTicker("KSPI"))
                .assertNext(summary -> {
                    assertThat(summary).isNotNull();
                    assertThat(summary.getTicker()).isEqualTo("KSPI");
                    assertThat(summary.getCurrentPrice()).isEqualByComparingTo(BigDecimal.valueOf(17000.0));
                    assertThat(summary.getHistory()).hasSize(2);
                    assertThat(summary.getNextDividend()).isNotNull();
                    assertThat(summary.getNextDividend().getAmountPerShare()).isEqualByComparingTo(BigDecimal.valueOf(850.0));
                    // LTM paid: 850, price 17000 => yield 5.00%
                    assertThat(summary.getTrailingTwelveMonthsYieldPercent()).isEqualByComparingTo(BigDecimal.valueOf(5.0));
                })
                .verifyComplete();
    }
}
