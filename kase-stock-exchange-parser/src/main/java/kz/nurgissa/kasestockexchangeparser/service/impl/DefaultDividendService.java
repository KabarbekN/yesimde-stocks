package kz.nurgissa.kasestockexchangeparser.service.impl;

import kz.nurgissa.kasestockexchangeparser.model.dtos.DividendItemDto;
import kz.nurgissa.kasestockexchangeparser.model.dtos.DividendSummaryDto;
import kz.nurgissa.kasestockexchangeparser.model.entities.DividendEventEntity;
import kz.nurgissa.kasestockexchangeparser.model.entities.SecurityInstrumentEntity;
import kz.nurgissa.kasestockexchangeparser.repositories.DividendEventRepository;
import kz.nurgissa.kasestockexchangeparser.repositories.SecurityInstrumentRepository;
import kz.nurgissa.kasestockexchangeparser.service.DividendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultDividendService implements DividendService {

    private final DividendEventRepository dividendEventRepository;
    private final SecurityInstrumentRepository securityInstrumentRepository;

    @Override
    public Mono<List<DividendItemDto>> getUpcomingDividends() {
        LocalDate today = LocalDate.now();
        return dividendEventRepository.findUpcomingDividends(today)
                .collectList()
                .flatMap(upcoming -> {
                    if (upcoming.isEmpty()) {
                        // Fallback to latest dividends if no upcoming
                        return dividendEventRepository.findLatestDividends(15).collectList();
                    }
                    return Mono.just(upcoming);
                })
                .flatMapMany(Flux::fromIterable)
                .flatMap(this::enrichWithMarketData)
                .sort(Comparator.comparing(DividendItemDto::getRecordDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .collectList();
    }

    @Override
    public Mono<DividendSummaryDto> getDividendSummaryByTicker(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            return Mono.empty();
        }
        String cleanTicker = ticker.trim().toUpperCase();

        Mono<SecurityInstrumentEntity> stockMono = securityInstrumentRepository.findByCode(cleanTicker)
                .defaultIfEmpty(new SecurityInstrumentEntity());

        Mono<List<DividendItemDto>> itemsMono = dividendEventRepository.findAllByTickerOrderByRecordDateDesc(cleanTicker)
                .flatMap(this::enrichWithMarketData)
                .collectList();

        return Mono.zip(stockMono, itemsMono)
                .map(tuple -> {
                    SecurityInstrumentEntity stock = tuple.getT1();
                    List<DividendItemDto> history = tuple.getT2();

                    if (history.isEmpty() && (stock.getId() == null)) {
                        return null;
                    }

                    BigDecimal currentPrice = (stock.getPrice() != null && stock.getPrice().compareTo(BigDecimal.ZERO) > 0)
                            ? stock.getPrice()
                            : (stock.getClosePrice() != null ? stock.getClosePrice() : null);

                    String companyName = !history.isEmpty() ? history.get(0).getCompanyName() : stock.getOrgNameRu();
                    String currency = stock.getCurrencyType() != null ? stock.getCurrencyType() : "KZT";

                    // Calculate LTM (Last Twelve Months) total paid
                    LocalDate today = LocalDate.now();
                    LocalDate oneYearAgo = today.minusYears(1);
                    BigDecimal ltmPaid = history.stream()
                            .filter(h -> h.getRecordDate() != null
                                    && !h.getRecordDate().isAfter(today)
                                    && h.getRecordDate().isAfter(oneYearAgo))
                            .map(DividendItemDto::getAmountPerShare)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal ltmYield = BigDecimal.ZERO;
                    if (currentPrice != null && currentPrice.compareTo(BigDecimal.ZERO) > 0 && ltmPaid.compareTo(BigDecimal.ZERO) > 0) {
                        ltmYield = ltmPaid.multiply(BigDecimal.valueOf(100))
                                .divide(currentPrice, 2, RoundingMode.HALF_UP);
                    }

                    // Next upcoming dividend
                    DividendItemDto nextDiv = history.stream()
                            .filter(h -> h.getRecordDate() != null && !h.getRecordDate().isBefore(today))
                            .min(Comparator.comparing(DividendItemDto::getRecordDate))
                            .orElse(null);

                    return DividendSummaryDto.builder()
                            .ticker(cleanTicker)
                            .companyName(companyName != null ? companyName : cleanTicker)
                            .currentPrice(currentPrice)
                            .currency(currency)
                            .trailingTwelveMonthsYieldPercent(ltmYield)
                            .totalPaidLastYear(ltmPaid)
                            .history(history)
                            .nextDividend(nextDiv)
                            .build();
                });
    }

    @Override
    public Mono<DividendEventEntity> saveDividendEvent(DividendEventEntity entity) {
        return dividendEventRepository.save(entity);
    }

    private Mono<DividendItemDto> enrichWithMarketData(DividendEventEntity entity) {
        return securityInstrumentRepository.findByCode(entity.getTicker())
                .map(stock -> {
                    BigDecimal price = (stock.getPrice() != null && stock.getPrice().compareTo(BigDecimal.ZERO) > 0)
                            ? stock.getPrice()
                            : (stock.getClosePrice() != null ? stock.getClosePrice() : null);
                    return toDto(entity, price);
                })
                .defaultIfEmpty(toDto(entity, null));
    }

    private DividendItemDto toDto(DividendEventEntity entity, BigDecimal currentPrice) {
        BigDecimal yieldPct = null;
        if (currentPrice != null && currentPrice.compareTo(BigDecimal.ZERO) > 0
                && entity.getAmountPerShare() != null && entity.getAmountPerShare().compareTo(BigDecimal.ZERO) > 0) {
            yieldPct = entity.getAmountPerShare().multiply(BigDecimal.valueOf(100))
                    .divide(currentPrice, 2, RoundingMode.HALF_UP);
        }

        Long daysUntil = null;
        if (entity.getRecordDate() != null) {
            daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), entity.getRecordDate());
        }

        return DividendItemDto.builder()
                .id(entity.getId())
                .ticker(entity.getTicker())
                .isin(entity.getIsin())
                .companyName(entity.getCompanyName())
                .recordDate(entity.getRecordDate())
                .paymentDate(entity.getPaymentDate())
                .announcementDate(entity.getAnnouncementDate())
                .amountPerShare(entity.getAmountPerShare())
                .currency(entity.getCurrency() != null ? entity.getCurrency() : "KZT")
                .period(entity.getPeriod())
                .status(entity.getStatus())
                .sourceUrl(entity.getSourceUrl())
                .currentStockPrice(currentPrice)
                .dividendYieldPercent(yieldPct)
                .daysUntilRecordDate(daysUntil)
                .build();
    }
}
