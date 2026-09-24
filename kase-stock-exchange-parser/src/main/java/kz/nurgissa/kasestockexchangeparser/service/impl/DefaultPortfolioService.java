package kz.nurgissa.kasestockexchangeparser.service.impl;

import kz.nurgissa.kasestockexchangeparser.model.dtos.PortfolioPositionDto;
import kz.nurgissa.kasestockexchangeparser.model.dtos.PortfolioSummaryDto;
import kz.nurgissa.kasestockexchangeparser.model.entities.DividendEventEntity;
import kz.nurgissa.kasestockexchangeparser.model.entities.SecurityInstrumentEntity;
import kz.nurgissa.kasestockexchangeparser.model.entities.UserPortfolioEntity;
import kz.nurgissa.kasestockexchangeparser.repositories.DividendEventRepository;
import kz.nurgissa.kasestockexchangeparser.repositories.SecurityInstrumentRepository;
import kz.nurgissa.kasestockexchangeparser.repositories.UserPortfolioRepository;
import kz.nurgissa.kasestockexchangeparser.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultPortfolioService implements PortfolioService {

    private final UserPortfolioRepository userPortfolioRepository;
    private final SecurityInstrumentRepository securityInstrumentRepository;
    private final DividendEventRepository dividendEventRepository;

    @Override
    public Mono<PortfolioSummaryDto> getPortfolioSummary(Long chatId) {
        if (chatId == null) {
            return Mono.empty();
        }

        return userPortfolioRepository.findAllByChatIdOrderByTickerAsc(chatId)
                .flatMap(this::enrichPosition)
                .collectList()
                .map(positions -> buildSummary(chatId, positions));
    }

    @Override
    public Mono<PortfolioPositionDto> addOrUpdatePosition(Long chatId, String ticker, BigDecimal quantity, BigDecimal buyPrice) {
        if (chatId == null || ticker == null || ticker.isBlank()) {
            return Mono.error(new IllegalArgumentException("chatId and ticker must not be empty"));
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return Mono.error(new IllegalArgumentException("Quantity must be greater than zero"));
        }

        String cleanTicker = ticker.trim().toUpperCase();

        return securityInstrumentRepository.findByCode(cleanTicker)
                .defaultIfEmpty(new SecurityInstrumentEntity())
                .flatMap(stock -> {
                    BigDecimal resolvedPrice = buyPrice;
                    if (resolvedPrice == null || resolvedPrice.compareTo(BigDecimal.ZERO) <= 0) {
                        resolvedPrice = (stock.getPrice() != null && stock.getPrice().compareTo(BigDecimal.ZERO) > 0)
                                ? stock.getPrice()
                                : (stock.getClosePrice() != null ? stock.getClosePrice() : BigDecimal.ZERO);
                    }
                    BigDecimal finalBuyPrice = resolvedPrice;
                    String currency = stock.getCurrencyType() != null ? stock.getCurrencyType() : "KZT";

                    return userPortfolioRepository.findByChatIdAndTicker(chatId, cleanTicker)
                            .flatMap(existing -> {
                                existing.setQuantity(quantity);
                                existing.setBuyPrice(finalBuyPrice);
                                existing.setCurrency(currency);
                                existing.setUpdatedAt(LocalDateTime.now());
                                return userPortfolioRepository.save(existing);
                            })
                            .switchIfEmpty(Mono.defer(() -> {
                                UserPortfolioEntity newEntity = UserPortfolioEntity.builder()
                                        .chatId(chatId)
                                        .ticker(cleanTicker)
                                        .quantity(quantity)
                                        .buyPrice(finalBuyPrice)
                                        .currency(currency)
                                        .createdAt(LocalDateTime.now())
                                        .updatedAt(LocalDateTime.now())
                                        .build();
                                return userPortfolioRepository.save(newEntity);
                            }))
                            .flatMap(this::enrichPosition);
                });
    }

    @Override
    public Mono<Void> removePosition(Long chatId, String ticker) {
        if (chatId == null || ticker == null) {
            return Mono.empty();
        }
        return userPortfolioRepository.deleteByChatIdAndTicker(chatId, ticker.trim().toUpperCase()).then();
    }

    @Override
    public Mono<Void> clearPortfolio(Long chatId) {
        if (chatId == null) {
            return Mono.empty();
        }
        return userPortfolioRepository.deleteAllByChatId(chatId).then();
    }

    private Mono<PortfolioPositionDto> enrichPosition(UserPortfolioEntity entity) {
        Mono<SecurityInstrumentEntity> stockMono = securityInstrumentRepository.findByCode(entity.getTicker())
                .defaultIfEmpty(new SecurityInstrumentEntity());

        Mono<List<DividendEventEntity>> divMono = dividendEventRepository.findAllByTickerOrderByRecordDateDesc(entity.getTicker())
                .collectList()
                .defaultIfEmpty(List.of());

        return Mono.zip(stockMono, divMono)
                .map(tuple -> {
                    SecurityInstrumentEntity stock = tuple.getT1();
                    List<DividendEventEntity> divs = tuple.getT2();

                    BigDecimal qty = entity.getQuantity() != null ? entity.getQuantity() : BigDecimal.ONE;
                    BigDecimal currentPrice = (stock.getPrice() != null && stock.getPrice().compareTo(BigDecimal.ZERO) > 0)
                            ? stock.getPrice()
                            : (stock.getClosePrice() != null && stock.getClosePrice().compareTo(BigDecimal.ZERO) > 0
                                ? stock.getClosePrice()
                                : (entity.getBuyPrice() != null ? entity.getBuyPrice() : BigDecimal.ZERO));

                    BigDecimal buyPrice = (entity.getBuyPrice() != null && entity.getBuyPrice().compareTo(BigDecimal.ZERO) > 0)
                            ? entity.getBuyPrice()
                            : currentPrice;

                    BigDecimal totalCost = qty.multiply(buyPrice);
                    BigDecimal currentValue = qty.multiply(currentPrice);
                    BigDecimal unrealizedPnl = currentValue.subtract(totalCost);

                    BigDecimal pnlPercent = BigDecimal.ZERO;
                    if (buyPrice.compareTo(BigDecimal.ZERO) > 0) {
                        pnlPercent = currentPrice.subtract(buyPrice)
                                .multiply(BigDecimal.valueOf(100))
                                .divide(buyPrice, 2, RoundingMode.HALF_UP);
                    }

                    // Annual dividend estimate
                    LocalDate oneYearAgo = LocalDate.now().minusYears(1);
                    BigDecimal annualDivPerShare = divs.stream()
                            .filter(d -> d.getRecordDate() != null && d.getRecordDate().isAfter(oneYearAgo))
                            .map(DividendEventEntity::getAmountPerShare)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal totalExpectedDivs = annualDivPerShare.multiply(qty);

                    BigDecimal yieldOnCost = BigDecimal.ZERO;
                    if (buyPrice.compareTo(BigDecimal.ZERO) > 0 && annualDivPerShare.compareTo(BigDecimal.ZERO) > 0) {
                        yieldOnCost = annualDivPerShare.multiply(BigDecimal.valueOf(100))
                                .divide(buyPrice, 2, RoundingMode.HALF_UP);
                    }

                    String name = stock.getOrgNameRu() != null ? stock.getOrgNameRu() : entity.getTicker();

                    return PortfolioPositionDto.builder()
                            .ticker(entity.getTicker())
                            .companyName(name)
                            .quantity(qty)
                            .buyPrice(buyPrice)
                            .currentPrice(currentPrice)
                            .currency(entity.getCurrency() != null ? entity.getCurrency() : "KZT")
                            .totalCost(totalCost)
                            .currentValue(currentValue)
                            .unrealizedPnl(unrealizedPnl)
                            .unrealizedPnlPercent(pnlPercent)
                            .expectedAnnualDividends(totalExpectedDivs)
                            .dividendYieldOnCost(yieldOnCost)
                            .build();
                });
    }

    private PortfolioSummaryDto buildSummary(Long chatId, List<PortfolioPositionDto> positions) {
        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal totalCurrentValue = BigDecimal.ZERO;
        BigDecimal totalExpectedDividends = BigDecimal.ZERO;

        for (PortfolioPositionDto p : positions) {
            totalInvested = totalInvested.add(p.getTotalCost());
            totalCurrentValue = totalCurrentValue.add(p.getCurrentValue());
            totalExpectedDividends = totalExpectedDividends.add(p.getExpectedAnnualDividends() != null ? p.getExpectedAnnualDividends() : BigDecimal.ZERO);
        }

        BigDecimal totalPnl = totalCurrentValue.subtract(totalInvested);
        BigDecimal totalPnlPercent = BigDecimal.ZERO;
        if (totalInvested.compareTo(BigDecimal.ZERO) > 0) {
            totalPnlPercent = totalPnl.multiply(BigDecimal.valueOf(100))
                    .divide(totalInvested, 2, RoundingMode.HALF_UP);
        }

        BigDecimal portfolioDivYield = BigDecimal.ZERO;
        if (totalCurrentValue.compareTo(BigDecimal.ZERO) > 0 && totalExpectedDividends.compareTo(BigDecimal.ZERO) > 0) {
            portfolioDivYield = totalExpectedDividends.multiply(BigDecimal.valueOf(100))
                    .divide(totalCurrentValue, 2, RoundingMode.HALF_UP);
        }

        // Calculate portfolio share for each position
        List<PortfolioPositionDto> finalPositions = new ArrayList<>();
        for (PortfolioPositionDto p : positions) {
            BigDecimal share = BigDecimal.ZERO;
            if (totalCurrentValue.compareTo(BigDecimal.ZERO) > 0) {
                share = p.getCurrentValue().multiply(BigDecimal.valueOf(100))
                        .divide(totalCurrentValue, 2, RoundingMode.HALF_UP);
            }
            p.setPortfolioSharePercent(share);
            finalPositions.add(p);
        }

        return PortfolioSummaryDto.builder()
                .chatId(chatId)
                .totalPositionsCount(finalPositions.size())
                .totalInvested(totalInvested)
                .totalCurrentValue(totalCurrentValue)
                .totalUnrealizedPnl(totalPnl)
                .totalUnrealizedPnlPercent(totalPnlPercent)
                .totalExpectedAnnualDividends(totalExpectedDividends)
                .portfolioDividendYieldPercent(portfolioDivYield)
                .positions(finalPositions)
                .build();
    }
}
