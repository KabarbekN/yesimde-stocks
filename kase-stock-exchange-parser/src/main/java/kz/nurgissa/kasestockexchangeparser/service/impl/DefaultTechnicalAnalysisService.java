package kz.nurgissa.kasestockexchangeparser.service.impl;

import kz.nurgissa.kasestockexchangeparser.client.AixClient;
import kz.nurgissa.kasestockexchangeparser.model.dtos.TechnicalAnalysisDto;
import kz.nurgissa.kasestockexchangeparser.model.entities.AixInstrumentEntity;
import kz.nurgissa.kasestockexchangeparser.model.entities.SecurityInstrumentEntity;
import kz.nurgissa.kasestockexchangeparser.model.entities.SecurityPriceHistoryEntity;
import kz.nurgissa.kasestockexchangeparser.repositories.AixSecurityInstrumentRepository;
import kz.nurgissa.kasestockexchangeparser.repositories.SecurityInstrumentRepository;
import kz.nurgissa.kasestockexchangeparser.repositories.SecurityPriceHistoryRepository;
import kz.nurgissa.kasestockexchangeparser.service.TechnicalAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultTechnicalAnalysisService implements TechnicalAnalysisService {

    private final SecurityInstrumentRepository securityInstrumentRepository;
    private final SecurityPriceHistoryRepository priceHistoryRepository;
    private final AixSecurityInstrumentRepository aixRepository;
    private final AixClient aixClient;

    private static final List<String> DEFAULT_PULSE_TICKERS = List.of(
            "AIRA", "CCBN", "HSBK", "KMGZ", "KSPI", "KZAP", "KZTK", "KEGC", "BCKP"
    );

    @Override
    public Mono<TechnicalAnalysisDto> analyzeInstrument(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            return Mono.empty();
        }

        String cleanTicker = ticker.trim().toUpperCase();

        // 1. Try KASE instrument first
        return securityInstrumentRepository.findByCode(cleanTicker)
                .flatMap(this::buildKaseAnalysis)
                .switchIfEmpty(Mono.defer(() -> {
                    // Check AIX alias (e.g. KAP -> KZAP)
                    if ("KAP".equalsIgnoreCase(cleanTicker)) {
                        return securityInstrumentRepository.findByCode("KZAP")
                                .flatMap(this::buildKaseAnalysis);
                    }
                    return Mono.empty();
                }))
                .switchIfEmpty(Mono.defer(() -> analyzeAixInstrument(cleanTicker)));
    }

    private Mono<TechnicalAnalysisDto> buildKaseAnalysis(SecurityInstrumentEntity instr) {
        List<BigDecimal> prices = parseSparkline(instr.getMonthlySparkLine());

        BigDecimal currentPrice = instr.getPrice() != null ? instr.getPrice() : instr.getClosePrice();

        if (prices.size() < 5) {
            // Fallback to security_price_history if sparkline is too short
            return priceHistoryRepository.findRecentByInstrumentId(instr.getId(), 30)
                    .map(SecurityPriceHistoryEntity::getPrice)
                    .filter(Objects::nonNull)
                    .collectList()
                    .map(historyList -> {
                        List<BigDecimal> combined = new ArrayList<>(historyList);
                        Collections.reverse(combined); // chronological
                        if (currentPrice != null && (combined.isEmpty() || !combined.get(combined.size() - 1).equals(currentPrice))) {
                            combined.add(currentPrice);
                        }
                        return buildDtoFromPrices(instr, combined, currentPrice);
                    })
                    .defaultIfEmpty(buildDtoFromPrices(instr, prices, currentPrice));
        }

        List<BigDecimal> combined = new ArrayList<>(prices);
        if (currentPrice != null && (combined.isEmpty() || !combined.get(combined.size() - 1).equals(currentPrice))) {
            combined.add(currentPrice);
        }

        return Mono.just(buildDtoFromPrices(instr, combined, currentPrice));
    }

    private TechnicalAnalysisDto buildDtoFromPrices(SecurityInstrumentEntity instr, List<BigDecimal> prices, BigDecimal currentPrice) {
        BigDecimal rsi = calculateRsi(prices, 14);
        BigDecimal sma20 = calculateSma(prices, 20);

        BigDecimal dayLow = prices.isEmpty() ? currentPrice : prices.stream().min(BigDecimal::compareTo).orElse(currentPrice);
        BigDecimal dayHigh = prices.isEmpty() ? currentPrice : prices.stream().max(BigDecimal::compareTo).orElse(currentPrice);

        BigDecimal priceVsSma = null;
        String trendSignal = "NEUTRAL";
        if (currentPrice != null && sma20 != null && sma20.compareTo(BigDecimal.ZERO) > 0) {
            priceVsSma = currentPrice.subtract(sma20)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(sma20, 2, RoundingMode.HALF_UP);
            if (priceVsSma.compareTo(BigDecimal.valueOf(0.5)) > 0) {
                trendSignal = "BULLISH";
            } else if (priceVsSma.compareTo(BigDecimal.valueOf(-0.5)) < 0) {
                trendSignal = "BEARISH";
            }
        }

        String rsiStatus = "NEUTRAL";
        if (rsi != null) {
            if (rsi.compareTo(BigDecimal.valueOf(70)) >= 0) {
                rsiStatus = "OVERBOUGHT";
            } else if (rsi.compareTo(BigDecimal.valueOf(30)) <= 0) {
                rsiStatus = "OVERSOLD";
            }
        }

        String name = instr.getOrgShortNameRu() != null ? instr.getOrgShortNameRu() : instr.getOrgNameRu();
        String rec = generateRecommendation(rsi, rsiStatus, trendSignal, priceVsSma);

        return TechnicalAnalysisDto.builder()
                .ticker(instr.getCode())
                .name(name != null ? name : instr.getCode())
                .exchange("KASE")
                .currentPrice(currentPrice)
                .currency(instr.getCurrencyType() != null ? instr.getCurrencyType() : "KZT")
                .change(instr.getTrand())
                .changePercent(instr.getTrandPercent())
                .dayLow(dayLow)
                .dayHigh(dayHigh)
                .rsi14(rsi)
                .rsiStatus(rsiStatus)
                .sma20(sma20)
                .priceVsSmaPercent(priceVsSma)
                .trendSignal(trendSignal)
                .supportLevel(dayLow)
                .resistanceLevel(dayHigh)
                .recommendation(rec)
                .historyPointsCount(prices.size())
                .build();
    }

    private Mono<TechnicalAnalysisDto> analyzeAixInstrument(String ticker) {
        return aixRepository.findBySecCode(ticker)
                .flatMap(entity -> aixClient.fetchTradingSummary(ticker)
                        .map(summary -> buildAixDto(entity, summary))
                        .defaultIfEmpty(buildAixDto(entity, null))
                );
    }

    private TechnicalAnalysisDto buildAixDto(AixInstrumentEntity entity, Object summaryObj) {
        BigDecimal curPrice = entity.getLastTrade() != null ? entity.getLastTrade() : entity.getReferencePrice();
        BigDecimal high = null;
        BigDecimal low = null;
        BigDecimal change = entity.getPriceChange();
        BigDecimal changePct = entity.getPercentChange();

        if (summaryObj instanceof kz.nurgissa.kasestockexchangeparser.model.dtos.AixInstrumentDto summary) {
            if (summary.getHighPrice() != null) high = summary.getHighPrice();
            if (summary.getLowPrice() != null) low = summary.getLowPrice();
            if (summary.getLastTrade() != null) curPrice = summary.getLastTrade();
            if (summary.getPercentChange() != null) changePct = summary.getPercentChange();
            if (summary.getPriceChange() != null) change = summary.getPriceChange();
        }

        String name = entity.getShortName() != null ? entity.getShortName() : entity.getIssuer();

        return TechnicalAnalysisDto.builder()
                .ticker(entity.getSecCode())
                .name(name != null ? name : entity.getSecCode())
                .exchange("AIX")
                .currentPrice(curPrice)
                .currency(entity.getCurrency() != null ? entity.getCurrency() : "KZT")
                .change(change)
                .changePercent(changePct)
                .dayLow(low != null ? low : curPrice)
                .dayHigh(high != null ? high : curPrice)
                .rsi14(null)
                .rsiStatus("NEUTRAL")
                .sma20(null)
                .priceVsSmaPercent(null)
                .trendSignal("NEUTRAL")
                .supportLevel(low != null ? low : curPrice)
                .resistanceLevel(high != null ? high : curPrice)
                .recommendation("Инструмент торгуется на бирже AIX. См. текущий стакан заявок /depth " + entity.getSecCode())
                .historyPointsCount(1)
                .build();
    }

    @Override
    public Mono<List<TechnicalAnalysisDto>> getMarketPulse(List<String> tickers) {
        List<String> targetTickers = (tickers != null && !tickers.isEmpty()) ? tickers : DEFAULT_PULSE_TICKERS;

        return Flux.fromIterable(targetTickers)
                .flatMap(this::analyzeInstrument)
                .collectList()
                .map(list -> {
                    list.sort(Comparator.comparing(TechnicalAnalysisDto::getTicker));
                    return list;
                });
    }

    public static BigDecimal calculateRsi(List<BigDecimal> prices, int period) {
        if (prices == null || prices.size() < 2) {
            return null;
        }

        int n = Math.min(period > 0 ? period : 14, prices.size() - 1);
        BigDecimal sumGain = BigDecimal.ZERO;
        BigDecimal sumLoss = BigDecimal.ZERO;

        for (int i = 1; i <= n; i++) {
            BigDecimal diff = prices.get(i).subtract(prices.get(i - 1));
            if (diff.compareTo(BigDecimal.ZERO) > 0) {
                sumGain = sumGain.add(diff);
            } else if (diff.compareTo(BigDecimal.ZERO) < 0) {
                sumLoss = sumLoss.add(diff.abs());
            }
        }

        BigDecimal avgGain = sumGain.divide(BigDecimal.valueOf(n), 6, RoundingMode.HALF_UP);
        BigDecimal avgLoss = sumLoss.divide(BigDecimal.valueOf(n), 6, RoundingMode.HALF_UP);

        for (int i = n + 1; i < prices.size(); i++) {
            BigDecimal diff = prices.get(i).subtract(prices.get(i - 1));
            BigDecimal gain = diff.compareTo(BigDecimal.ZERO) > 0 ? diff : BigDecimal.ZERO;
            BigDecimal loss = diff.compareTo(BigDecimal.ZERO) < 0 ? diff.abs() : BigDecimal.ZERO;

            avgGain = avgGain.multiply(BigDecimal.valueOf(n - 1)).add(gain).divide(BigDecimal.valueOf(n), 6, RoundingMode.HALF_UP);
            avgLoss = avgLoss.multiply(BigDecimal.valueOf(n - 1)).add(loss).divide(BigDecimal.valueOf(n), 6, RoundingMode.HALF_UP);
        }

        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(100.00).setScale(2, RoundingMode.HALF_UP);
        }
        if (avgGain.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal rs = avgGain.divide(avgLoss, 6, RoundingMode.HALF_UP);
        BigDecimal rsi = BigDecimal.valueOf(100).subtract(
                BigDecimal.valueOf(100).divide(BigDecimal.ONE.add(rs), 2, RoundingMode.HALF_UP)
        );

        return rsi.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal calculateSma(List<BigDecimal> prices, int period) {
        if (prices == null || prices.isEmpty()) {
            return null;
        }
        int k = Math.min(period > 0 ? period : 20, prices.size());
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = prices.size() - k; i < prices.size(); i++) {
            sum = sum.add(prices.get(i));
        }
        return sum.divide(BigDecimal.valueOf(k), 2, RoundingMode.HALF_UP);
    }

    private List<BigDecimal> parseSparkline(String sparklineStr) {
        if (sparklineStr == null || sparklineStr.isBlank()) return List.of();
        try {
            return Arrays.stream(sparklineStr.split(";"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> {
                        try {
                            return new BigDecimal(s);
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    private String generateRecommendation(BigDecimal rsi, String rsiStatus, String trendSignal, BigDecimal priceVsSma) {
        StringBuilder sb = new StringBuilder();

        if ("OVERBOUGHT".equals(rsiStatus)) {
            sb.append("🔴 <b>Перекуплен</b> (RSI = ").append(rsi).append("). Инструмент находится в зоне сильной покупки, повышен риск фиксации прибыли и коррекции вниз.");
        } else if ("OVERSOLD".equals(rsiStatus)) {
            sb.append("🟢 <b>Перепродан</b> (RSI = ").append(rsi).append("). Инструмент на сильном спаде, потенциально привлекательная точка для подбора в лонг.");
        } else {
            sb.append("⚖️ <b>Нейтральная зона</b> (RSI = ").append(rsi != null ? rsi : "—").append("). Экстремальной перекупленности/перепроданности нет.");
        }

        if ("BULLISH".equals(trendSignal)) {
            sb.append(" Тренд выше SMA-20 (+").append(priceVsSma).append("%), динамика бычья 🐂.");
        } else if ("BEARISH".equals(trendSignal)) {
            sb.append(" Тренд ниже SMA-20 (").append(priceVsSma).append("%), динамика нисходящая 🐻.");
        }

        return sb.toString();
    }
}
