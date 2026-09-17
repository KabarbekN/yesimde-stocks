package kz.nurgissa.kasestockexchangeparser.scheduler;

import kz.nurgissa.kasestockexchangeparser.model.dtos.BondItemDto;
import kz.nurgissa.kasestockexchangeparser.model.dtos.MarketSummaryDto;
import kz.nurgissa.kasestockexchangeparser.model.dtos.StockItemDto;
import kz.nurgissa.kasestockexchangeparser.service.BondAnalyticsService;
import kz.nurgissa.kasestockexchangeparser.telegram.TelegramAlertDispatcherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarketAlertDetectorScheduler {

    private final BondAnalyticsService analyticsService;
    private final TelegramAlertDispatcherService alertDispatcherService;

    /**
     * Periodic scanner for discounts, significant stock moves, and institutional deals.
     * Runs every 2 minutes after initial 1-minute delay.
     */
    @Scheduled(fixedDelay = 120_000, initialDelay = 60_000)
    public void scanMarketAlerts() {
        if (!alertDispatcherService.isEnabled()) {
            return;
        }

        // 1. Scan for discounted bonds (<= 95% of face value)
        analyticsService.getDiscountBonds(95.0)
                .flatMapMany(Flux::fromIterable)
                .flatMap(alertDispatcherService::broadcastDiscountAlert)
                .onErrorResume(e -> {
                    log.warn("Error checking discount bond alerts: {}", e.getMessage());
                    return Flux.empty();
                })
                .subscribe();

        // 2. Scan for stock price movements (>= 3% daily change)
        analyticsService.getTopStocks(null)
                .flatMapMany(Flux::fromIterable)
                .filter(s -> s.getChangePercent() != null && s.getChangePercent().abs().compareTo(BigDecimal.valueOf(3.0)) >= 0)
                .flatMap(alertDispatcherService::broadcastStockMoveAlert)
                .onErrorResume(e -> {
                    log.warn("Error checking stock movement alerts: {}", e.getMessage());
                    return Flux.empty();
                })
                .subscribe();

        // 3. Scan for whale deals (> 500 mln KZT)
        analyticsService.getMarketSummary()
                .flatMapMany(summary -> Flux.fromIterable(summary.getRecentAnomalies() != null ? summary.getRecentAnomalies() : java.util.Collections.<MarketSummaryDto.AnomalyItemDto>emptyList()))
                .filter(a -> a.getMetricValue() != null && a.getMetricValue().compareTo(BigDecimal.valueOf(500_000_000L)) > 0)
                .flatMap(a -> alertDispatcherService.broadcastWhaleAlert(a.getCode(), a.getName(), a.getMetricValue(), null))
                .onErrorResume(e -> {
                    log.warn("Error checking whale deal alerts: {}", e.getMessage());
                    return Flux.empty();
                })
                .subscribe();
    }

    /**
     * Weekly coupon calendar broadcast on Monday at 09:00 AM Almaty time.
     */
    @Scheduled(cron = "0 0 9 * * MON", zone = "Asia/Almaty")
    public void sendWeeklyCouponCalendar() {
        if (!alertDispatcherService.isEnabled()) {
            return;
        }

        log.info("Triggering weekly coupon calendar broadcast...");
        analyticsService.getUpcomingCouponBonds()
                .flatMap(alertDispatcherService::broadcastWeeklyCouponCalendar)
                .doOnSuccess(v -> log.info("Weekly coupon calendar sent successfully."))
                .doOnError(e -> log.error("Failed to send weekly coupon calendar: {}", e.getMessage()))
                .subscribe();
    }
}
