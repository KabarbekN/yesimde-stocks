package kz.nurgissa.kasestockexchangeparser.telegram;

import kz.nurgissa.kasestockexchangeparser.model.dtos.BondItemDto;
import kz.nurgissa.kasestockexchangeparser.model.dtos.StockItemDto;
import kz.nurgissa.kasestockexchangeparser.repositories.AlertCooldownRepository;
import kz.nurgissa.kasestockexchangeparser.repositories.PriceAlertTargetRepository;
import kz.nurgissa.kasestockexchangeparser.repositories.TelegramSubscriberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class TelegramAlertDispatcherServiceTest {

    private TelegramSubscriberRepository subscriberRepository;
    private AlertCooldownRepository cooldownRepository;
    private PriceAlertTargetRepository priceAlertTargetRepository;
    private TelegramAlertDispatcherService dispatcherService;

    @BeforeEach
    void setUp() {
        subscriberRepository = Mockito.mock(TelegramSubscriberRepository.class);
        cooldownRepository = Mockito.mock(AlertCooldownRepository.class);
        priceAlertTargetRepository = Mockito.mock(PriceAlertTargetRepository.class);
        // disabled bot mode (e.g. without token)
        dispatcherService = new TelegramAlertDispatcherService(
                subscriberRepository,
                cooldownRepository,
                priceAlertTargetRepository,
                "",
                false
        );
    }

    @Test
    void isEnabled_shouldReturnFalseWhenTokenEmptyOrDisabled() {
        assertFalse(dispatcherService.isEnabled());
    }

    @Test
    void broadcastDiscountAlert_shouldCompleteGracefullyWhenDisabled() {
        BondItemDto bond = BondItemDto.builder()
                .code("BIDBb5")
                .orgNameRu("BI Group")
                .price(BigDecimal.valueOf(93.5))
                .cupon(BigDecimal.valueOf(19.5))
                .dohod(BigDecimal.valueOf(22.1))
                .dtm(730)
                .currency("KZT")
                .faceValue(BigDecimal.valueOf(1000))
                .build();

        StepVerifier.create(dispatcherService.broadcastDiscountAlert(bond))
                .verifyComplete();
    }

    @Test
    void broadcastStockMoveAlert_shouldCompleteGracefullyWhenDisabled() {
        StockItemDto stock = StockItemDto.builder()
                .code("KSPI")
                .name("Kaspi.kz")
                .price(BigDecimal.valueOf(55000))
                .change(BigDecimal.valueOf(2500))
                .changePercent(BigDecimal.valueOf(4.5))
                .volumeKzt(BigDecimal.valueOf(800000000L))
                .dealCount(150)
                .currency("KZT")
                .build();
        StepVerifier.create(dispatcherService.broadcastStockMoveAlert(stock))
                .verifyComplete();
    }

    @Test
    void checkAndDispatchPriceTargets_shouldCompleteGracefullyWhenDisabled() {
        StepVerifier.create(dispatcherService.checkAndDispatchPriceTargets("KSPI", BigDecimal.valueOf(55000)))
                .verifyComplete();
    }
}

