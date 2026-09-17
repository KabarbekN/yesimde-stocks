package kz.nurgissa.kasestockexchangeparser.service;

import kz.nurgissa.kasestockexchangeparser.service.impl.DefaultTechnicalAnalysisService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TechnicalAnalysisServiceTest {

    @Test
    void calculateRsi_shouldReturn100WhenOnlyGains() {
        List<BigDecimal> prices = List.of(
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(105),
                BigDecimal.valueOf(110),
                BigDecimal.valueOf(115),
                BigDecimal.valueOf(120)
        );

        BigDecimal rsi = DefaultTechnicalAnalysisService.calculateRsi(prices, 14);
        assertNotNull(rsi);
        assertEquals(100.00, rsi.doubleValue(), 0.01);
    }

    @Test
    void calculateRsi_shouldReturn0WhenOnlyLosses() {
        List<BigDecimal> prices = List.of(
                BigDecimal.valueOf(120),
                BigDecimal.valueOf(115),
                BigDecimal.valueOf(110),
                BigDecimal.valueOf(105),
                BigDecimal.valueOf(100)
        );

        BigDecimal rsi = DefaultTechnicalAnalysisService.calculateRsi(prices, 14);
        assertNotNull(rsi);
        assertEquals(0.00, rsi.doubleValue(), 0.01);
    }

    @Test
    void calculateRsi_shouldCalculateBalancedRsiAround50() {
        List<BigDecimal> prices = new ArrayList<>();
        prices.add(BigDecimal.valueOf(100));
        for (int i = 0; i < 10; i++) {
            prices.add(BigDecimal.valueOf(102));
            prices.add(BigDecimal.valueOf(100));
        }

        BigDecimal rsi = DefaultTechnicalAnalysisService.calculateRsi(prices, 14);
        assertNotNull(rsi);
        assertTrue(rsi.doubleValue() >= 45.0 && rsi.doubleValue() <= 55.0,
                "Balanced price swings should yield RSI around 50, got: " + rsi);
    }

    @Test
    void calculateRsi_shouldReturnNullWhenInsufficientPoints() {
        assertNull(DefaultTechnicalAnalysisService.calculateRsi(null, 14));
        assertNull(DefaultTechnicalAnalysisService.calculateRsi(List.of(BigDecimal.valueOf(100)), 14));
    }

    @Test
    void calculateSma_shouldCalculateAccurateAverage() {
        List<BigDecimal> prices = List.of(
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(20),
                BigDecimal.valueOf(30),
                BigDecimal.valueOf(40),
                BigDecimal.valueOf(50)
        );

        BigDecimal sma = DefaultTechnicalAnalysisService.calculateSma(prices, 5);
        assertNotNull(sma);
        assertEquals(30.00, sma.doubleValue(), 0.01);

        // Window smaller than list (last 3 elements: 30, 40, 50 -> avg 40)
        BigDecimal sma3 = DefaultTechnicalAnalysisService.calculateSma(prices, 3);
        assertNotNull(sma3);
        assertEquals(40.00, sma3.doubleValue(), 0.01);
    }
}
