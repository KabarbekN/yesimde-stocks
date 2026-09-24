package kz.nurgissa.kasestockexchangeparser.service;

import kz.nurgissa.kasestockexchangeparser.model.dtos.PortfolioPositionDto;
import kz.nurgissa.kasestockexchangeparser.model.dtos.PortfolioSummaryDto;
import kz.nurgissa.kasestockexchangeparser.model.entities.DividendEventEntity;
import kz.nurgissa.kasestockexchangeparser.model.entities.SecurityInstrumentEntity;
import kz.nurgissa.kasestockexchangeparser.model.entities.UserPortfolioEntity;
import kz.nurgissa.kasestockexchangeparser.repositories.DividendEventRepository;
import kz.nurgissa.kasestockexchangeparser.repositories.SecurityInstrumentRepository;
import kz.nurgissa.kasestockexchangeparser.repositories.UserPortfolioRepository;
import kz.nurgissa.kasestockexchangeparser.service.impl.DefaultPortfolioService;
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
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultPortfolioServiceTest {

    @Mock
    private UserPortfolioRepository userPortfolioRepository;

    @Mock
    private SecurityInstrumentRepository securityInstrumentRepository;

    @Mock
    private DividendEventRepository dividendEventRepository;

    private DefaultPortfolioService portfolioService;

    @BeforeEach
    void setUp() {
        portfolioService = new DefaultPortfolioService(
                userPortfolioRepository,
                securityInstrumentRepository,
                dividendEventRepository
        );
    }

    @Test
    void addOrUpdatePosition_withExplicitBuyPrice_shouldSaveAndEnrich() {
        Long chatId = 12345L;
        String ticker = "HSBK";
        BigDecimal qty = BigDecimal.valueOf(100);
        BigDecimal buyPrice = BigDecimal.valueOf(200);

        SecurityInstrumentEntity stock = SecurityInstrumentEntity.builder()
                .code("HSBK")
                .orgNameRu("АО Народный Банк Казахстана")
                .price(BigDecimal.valueOf(240))
                .currencyType("KZT")
                .build();

        UserPortfolioEntity savedEntity = UserPortfolioEntity.builder()
                .id(1L)
                .chatId(chatId)
                .ticker("HSBK")
                .quantity(qty)
                .buyPrice(buyPrice)
                .currency("KZT")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(securityInstrumentRepository.findByCode("HSBK"))
                .thenReturn(Mono.just(stock));
        when(userPortfolioRepository.findByChatIdAndTicker(chatId, "HSBK"))
                .thenReturn(Mono.empty());
        when(userPortfolioRepository.save(any(UserPortfolioEntity.class)))
                .thenReturn(Mono.just(savedEntity));
        when(dividendEventRepository.findAllByTickerOrderByRecordDateDesc("HSBK"))
                .thenReturn(Flux.empty());

        StepVerifier.create(portfolioService.addOrUpdatePosition(chatId, ticker, qty, buyPrice))
                .assertNext(pos -> {
                    assertThat(pos.getTicker()).isEqualTo("HSBK");
                    assertThat(pos.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(100));
                    assertThat(pos.getBuyPrice()).isEqualByComparingTo(BigDecimal.valueOf(200));
                    assertThat(pos.getCurrentPrice()).isEqualByComparingTo(BigDecimal.valueOf(240));
                    // totalCost = 100 * 200 = 20000
                    assertThat(pos.getTotalCost()).isEqualByComparingTo(BigDecimal.valueOf(20000));
                    // currentValue = 100 * 240 = 24000
                    assertThat(pos.getCurrentValue()).isEqualByComparingTo(BigDecimal.valueOf(24000));
                    // unrealizedPnl = 4000
                    assertThat(pos.getUnrealizedPnl()).isEqualByComparingTo(BigDecimal.valueOf(4000));
                    // unrealizedPnlPercent = +20%
                    assertThat(pos.getUnrealizedPnlPercent()).isEqualByComparingTo(BigDecimal.valueOf(20));
                })
                .verifyComplete();
    }

    @Test
    void getPortfolioSummary_shouldAggregateTotalsAndYields() {
        Long chatId = 12345L;

        UserPortfolioEntity pos1 = UserPortfolioEntity.builder()
                .chatId(chatId)
                .ticker("HSBK")
                .quantity(BigDecimal.valueOf(100))
                .buyPrice(BigDecimal.valueOf(200))
                .currency("KZT")
                .build();

        UserPortfolioEntity pos2 = UserPortfolioEntity.builder()
                .chatId(chatId)
                .ticker("KZAP")
                .quantity(BigDecimal.valueOf(10))
                .buyPrice(BigDecimal.valueOf(20000))
                .currency("KZT")
                .build();

        SecurityInstrumentEntity stock1 = SecurityInstrumentEntity.builder()
                .code("HSBK")
                .orgNameRu("АО Народный Банк Казахстана")
                .price(BigDecimal.valueOf(250))
                .currencyType("KZT")
                .build();

        SecurityInstrumentEntity stock2 = SecurityInstrumentEntity.builder()
                .code("KZAP")
                .orgNameRu("Казатомпром")
                .price(BigDecimal.valueOf(22000))
                .currencyType("KZT")
                .build();

        DividendEventEntity hsbkDiv = DividendEventEntity.builder()
                .ticker("HSBK")
                .amountPerShare(BigDecimal.valueOf(25))
                .recordDate(LocalDate.now().minusMonths(2))
                .build();

        when(userPortfolioRepository.findAllByChatIdOrderByTickerAsc(chatId))
                .thenReturn(Flux.just(pos1, pos2));
        when(securityInstrumentRepository.findByCode("HSBK"))
                .thenReturn(Mono.just(stock1));
        when(securityInstrumentRepository.findByCode("KZAP"))
                .thenReturn(Mono.just(stock2));
        when(dividendEventRepository.findAllByTickerOrderByRecordDateDesc("HSBK"))
                .thenReturn(Flux.just(hsbkDiv));
        when(dividendEventRepository.findAllByTickerOrderByRecordDateDesc("KZAP"))
                .thenReturn(Flux.empty());

        StepVerifier.create(portfolioService.getPortfolioSummary(chatId))
                .assertNext(summary -> {
                    assertThat(summary.getChatId()).isEqualTo(chatId);
                    assertThat(summary.getTotalPositionsCount()).isEqualTo(2);

                    // Pos 1 (HSBK): cost = 100 * 200 = 20,000; val = 100 * 250 = 25,000; div = 25 * 100 = 2,500
                    // Pos 2 (KZAP): cost = 10 * 20,000 = 200,000; val = 10 * 22,000 = 220,000; div = 0
                    // Total Invested = 220,000; Total Value = 245,000; PnL = +25,000
                    assertThat(summary.getTotalInvested()).isEqualByComparingTo(BigDecimal.valueOf(220000));
                    assertThat(summary.getTotalCurrentValue()).isEqualByComparingTo(BigDecimal.valueOf(245000));
                    assertThat(summary.getTotalUnrealizedPnl()).isEqualByComparingTo(BigDecimal.valueOf(25000));
                    assertThat(summary.getTotalExpectedAnnualDividends()).isEqualByComparingTo(BigDecimal.valueOf(2500));
                    assertThat(summary.getPositions()).hasSize(2);
                })
                .verifyComplete();
    }

    @Test
    void removePosition_shouldCallRepository() {
        Long chatId = 12345L;
        String ticker = "HSBK";

        when(userPortfolioRepository.deleteByChatIdAndTicker(chatId, "HSBK"))
                .thenReturn(Mono.empty());

        StepVerifier.create(portfolioService.removePosition(chatId, ticker))
                .verifyComplete();

        verify(userPortfolioRepository).deleteByChatIdAndTicker(chatId, "HSBK");
    }

    @Test
    void clearPortfolio_shouldCallRepository() {
        Long chatId = 12345L;

        when(userPortfolioRepository.deleteAllByChatId(chatId))
                .thenReturn(Mono.empty());

        StepVerifier.create(portfolioService.clearPortfolio(chatId))
                .verifyComplete();

        verify(userPortfolioRepository).deleteAllByChatId(chatId);
    }
}
