package kz.nurgissa.kasestockexchangeparser.service.impl;

import kz.nurgissa.kasestockexchangeparser.model.dtos.*;
import kz.nurgissa.kasestockexchangeparser.model.entities.SecurityMarketTurnoverEntity;
import kz.nurgissa.kasestockexchangeparser.service.AixService;
import kz.nurgissa.kasestockexchangeparser.service.KaseHistoricalStatsService;
import kz.nurgissa.kasestockexchangeparser.service.MarketBenchmarkService;
import kz.nurgissa.kasestockexchangeparser.service.ReportDataAggregatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultReportDataAggregatorService implements ReportDataAggregatorService {

    private final DatabaseClient databaseClient;
    private final MarketBenchmarkService benchmarkService;
    private final KaseHistoricalStatsService statsService;
    private final AixService aixService;

    @Override
    public Mono<ReportMarketSnapshotDto> buildMarketSnapshot(BigDecimal customCapital) {
        Mono<Map<String, BigDecimal>> benchmarksMono = benchmarkService.getBenchmarksMap();
        Mono<List<SecurityMarketTurnoverEntity>> cumulativeStatsMono = statsService.getCumulativeStats().collectList();
        Mono<List<SecurityMarketTurnoverEntity>> monthlyStatsMono = statsService.getMonthlyStats().collectList();
        Mono<List<ArbitrageItemDto>> arbitrageMono = aixService.getArbitrageOpportunities().defaultIfEmpty(new ArrayList<>());
        Mono<List<ReportMarketSnapshotDto.BondReportItemDto>> bondsMono = fetchAllLiveBonds();
        Mono<List<ReportMarketSnapshotDto.StockReportItemDto>> liveStocksMono = fetchLiveStocks();

        return Mono.zip(benchmarksMono, cumulativeStatsMono, monthlyStatsMono, arbitrageMono, bondsMono, liveStocksMono)
                .map(tuple -> {
                    Map<String, BigDecimal> benchmarks = tuple.getT1();
                    List<SecurityMarketTurnoverEntity> cumulativeStats = tuple.getT2();
                    List<SecurityMarketTurnoverEntity> monthlyStats = tuple.getT3();
                    List<ArbitrageItemDto> arbitragePairs = tuple.getT4();
                    List<ReportMarketSnapshotDto.BondReportItemDto> allBonds = tuple.getT5();
                    List<ReportMarketSnapshotDto.StockReportItemDto> liveStocks = tuple.getT6();

                    // Map monthly stats by code
                    Map<String, SecurityMarketTurnoverEntity> monthlyMap = monthlyStats.stream()
                            .collect(Collectors.toMap(s -> s.getCode().toUpperCase(), s -> s, (a, b) -> a));

                    // Join live stocks with historical turnover
                    Map<String, SecurityMarketTurnoverEntity> cumMap = cumulativeStats.stream()
                            .collect(Collectors.toMap(s -> s.getCode().toUpperCase(), s -> s, (a, b) -> a));

                    List<ReportMarketSnapshotDto.StockReportItemDto> fullStocks = new ArrayList<>();
                    for (ReportMarketSnapshotDto.StockReportItemDto s : liveStocks) {
                        String code = s.getCode().toUpperCase();
                        SecurityMarketTurnoverEntity cum = cumMap.get(code);
                        SecurityMarketTurnoverEntity mon = monthlyMap.get(code);

                        if (cum != null) {
                            s.setCumulativeVolumeKzt(cum.getVolumeKzt());
                            s.setCumulativeDeals(cum.getDealsCount());
                            s.setAvgDealSizeKzt(cum.getAvgDealSizeKzt());
                            s.setFreeFloatPct(cum.getFreeFloatPct());
                            s.setIsIpoSpo(cum.getIsIpoSpo());
                            s.setParticipantType(cum.getParticipantType());
                        }
                        if (mon != null) {
                            s.setMonthlyVolumeKzt(mon.getVolumeKzt());
                            s.setMonthlyDeals(mon.getDealsCount());
                            if (s.getFreeFloatPct() == null) s.setFreeFloatPct(mon.getFreeFloatPct());
                        }
                        fullStocks.add(s);
                    }

                    // Add historical stocks that might not be active in today's live feed
                    for (SecurityMarketTurnoverEntity cum : cumulativeStats) {
                        boolean exists = fullStocks.stream().anyMatch(s -> s.getCode().equalsIgnoreCase(cum.getCode()));
                        if (!exists) {
                            SecurityMarketTurnoverEntity mon = monthlyMap.get(cum.getCode().toUpperCase());
                            fullStocks.add(ReportMarketSnapshotDto.StockReportItemDto.builder()
                                    .code(cum.getCode())
                                    .name(cum.getCompanyName())
                                    .sector(cum.getSector())
                                    .exchange("KASE")
                                    .currentPrice(BigDecimal.ZERO)
                                    .currency("KZT")
                                    .dayChangePct(BigDecimal.ZERO)
                                    .cumulativeVolumeKzt(cum.getVolumeKzt())
                                    .cumulativeDeals(cum.getDealsCount())
                                    .monthlyVolumeKzt(mon != null ? mon.getVolumeKzt() : BigDecimal.ZERO)
                                    .monthlyDeals(mon != null ? mon.getDealsCount() : 0L)
                                    .avgDealSizeKzt(cum.getAvgDealSizeKzt())
                                    .freeFloatPct(cum.getFreeFloatPct())
                                    .isIpoSpo(cum.getIsIpoSpo())
                                    .participantType(cum.getParticipantType())
                                    .trend("Нейтральный")
                                    .build());
                        }
                    }

                    // Sort stocks by cumulative turnover descending
                    fullStocks.sort((a, b) -> {
                        BigDecimal v1 = a.getCumulativeVolumeKzt() != null ? a.getCumulativeVolumeKzt() : BigDecimal.ZERO;
                        BigDecimal v2 = b.getCumulativeVolumeKzt() != null ? b.getCumulativeVolumeKzt() : BigDecimal.ZERO;
                        return v2.compareTo(v1);
                    });

                    List<ReportMarketSnapshotDto.StockReportItemDto> topStocks = fullStocks.stream().limit(12).collect(Collectors.toList());

                    // Macro calculations
                    BigDecimal totalEquitiesTurnover = cumulativeStats.stream()
                            .map(SecurityMarketTurnoverEntity::getVolumeKzt)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    if (totalEquitiesTurnover.compareTo(BigDecimal.ZERO) == 0) {
                        totalEquitiesTurnover = new BigDecimal("2127860000000.00"); // 2.13 Tln fallback
                    }

                    long totalEquitiesDeals = cumulativeStats.stream()
                            .mapToLong(s -> s.getDealsCount() != null ? s.getDealsCount() : 0L)
                            .sum();
                    if (totalEquitiesDeals == 0) totalEquitiesDeals = 8423997L;

                    BigDecimal top3Turnover = fullStocks.stream().limit(3)
                            .map(ReportMarketSnapshotDto.StockReportItemDto::getCumulativeVolumeKzt)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal top3Concentration = totalEquitiesTurnover.compareTo(BigDecimal.ZERO) > 0
                            ? top3Turnover.multiply(BigDecimal.valueOf(100)).divide(totalEquitiesTurnover, 1, RoundingMode.HALF_UP)
                            : new BigDecimal("35.2");

                    BigDecimal baseRate = benchmarks.getOrDefault("KZ_BASE_RATE", new BigDecimal("16.25"));
                    BigDecimal inflationRate = benchmarks.getOrDefault("KZ_INFLATION_RATE", new BigDecimal("8.60"));

                    ReportMarketSnapshotDto.MacroReportStatsDto macro = ReportMarketSnapshotDto.MacroReportStatsDto.builder()
                            .totalEquitiesTurnoverKzt(totalEquitiesTurnover)
                            .totalEquitiesDeals(totalEquitiesDeals)
                            .top3ConcentrationPct(top3Concentration)
                            .fearAndGreedIndex(68)
                            .fearAndGreedLabel("GREED (Жадность)")
                            .baseRate(baseRate)
                            .inflationRate(inflationRate)
                            .build();

                    // Categorize Bonds
                    List<ReportMarketSnapshotDto.BondReportItemDto> quasigovBonds = allBonds.stream()
                            .filter(b -> "QUASIGOV".equalsIgnoreCase(b.getCategory()) || "SOVEREIGN".equalsIgnoreCase(b.getCategory()))
                            .sorted((a, b) -> b.getYtm().compareTo(a.getYtm()))
                            .limit(10)
                            .collect(Collectors.toList());

                    List<ReportMarketSnapshotDto.BondReportItemDto> discountBonds = allBonds.stream()
                            .filter(b -> b.getDiscountPct() != null && b.getDiscountPct().compareTo(new BigDecimal("3.0")) >= 0)
                            .sorted((a, b) -> b.getYtm().compareTo(a.getYtm()))
                            .limit(10)
                            .collect(Collectors.toList());

                    // Capital determination
                    BigDecimal krishaPrice = benchmarks.getOrDefault("KRISHA_ALMATY_1ROOM_PRICE", new BigDecimal("26500000.00"));
                    BigDecimal krishaRent = benchmarks.getOrDefault("KRISHA_ALMATY_1ROOM_RENT", new BigDecimal("230000.00"));
                    BigDecimal capital = (customCapital != null && customCapital.compareTo(BigDecimal.ZERO) > 0)
                            ? customCapital
                            : krishaPrice;

                    // Dynamic Battle Calculation
                    BigDecimal flexDepositRate = benchmarks.getOrDefault("DEPOSIT_FLEXIBLE_GESV", new BigDecimal("14.50"));
                    BigDecimal depositAnnualIncome = capital.multiply(flexDepositRate.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)).setScale(0, RoundingMode.HALF_UP);

                    // Real estate net yield: Rent * 12 * 0.85 (occupancy, tax, repair) / Price
                    BigDecimal grossRentYear = krishaRent.multiply(BigDecimal.valueOf(12));
                    BigDecimal netRentYear = grossRentYear.multiply(new BigDecimal("0.85")).setScale(0, RoundingMode.HALF_UP);
                    BigDecimal realEstateNetYield = netRentYear.divide(krishaPrice, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal realEstateIncomeOnCapital = capital.multiply(realEstateNetYield.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)).setScale(0, RoundingMode.HALF_UP);

                    // Top quasigov yield
                    BigDecimal topBondYield = !quasigovBonds.isEmpty() ? quasigovBonds.get(0).getYtm() : new BigDecimal("17.45");
                    BigDecimal bondAnnualIncome = capital.multiply(topBondYield.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)).setScale(0, RoundingMode.HALF_UP);

                    BigDecimal bondAdvantageDeposit = bondAnnualIncome.subtract(depositAnnualIncome);
                    BigDecimal multipleVsRe = realEstateIncomeOnCapital.compareTo(BigDecimal.ZERO) > 0
                            ? bondAnnualIncome.divide(realEstateIncomeOnCapital, 2, RoundingMode.HALF_UP)
                            : new BigDecimal("2.15");

                    ReportMarketSnapshotDto.AssetBattleComparisonDto battle = ReportMarketSnapshotDto.AssetBattleComparisonDto.builder()
                            .capitalAmount(capital)
                            .depositRate(flexDepositRate)
                            .depositAnnualIncome(depositAnnualIncome)
                            .depositNotes("Лимит КФГД 10-20 млн ₸. Нацбанк снижает ставку (16.25%), банки вскоре понизят ГЭСВ.")
                            .realEstatePrice(krishaPrice)
                            .monthlyRent(krishaRent)
                            .realEstateNetYield(realEstateNetYield)
                            .realEstateNetAnnualIncome(realEstateIncomeOnCapital)
                            .realEstateNotes("Krisha.kz (Алматы). Чистый доход за вычетом 1 мес. простоя, ремонта и налогов.")
                            .bondYield(topBondYield)
                            .bondAnnualIncome(bondAnnualIncome)
                            .bondAdvantageOverDeposit(bondAdvantageDeposit)
                            .bondAdvantageOverRealEstateMultiple(multipleVsRe)
                            .bondNotes("Ставка фиксируется на 3-5 лет (Отбасы, БРК, Самрук). Налог 0% по ст. 341 НК РК. Снятие без потери %.")
                            .build();

                    // 12 Months Paycheck Calendar
                    List<ReportMarketSnapshotDto.CouponMonthDto> calendar = build12MonthCalendar(capital);

                    return ReportMarketSnapshotDto.builder()
                            .generatedAt(LocalDateTime.now())
                            .investmentAmount(capital)
                            .macro(macro)
                            .battle(battle)
                            .topStocks(topStocks)
                            .allStocks(fullStocks)
                            .topQuasigovBonds(quasigovBonds)
                            .topDiscountBonds(discountBonds)
                            .allBonds(allBonds)
                            .arbitragePairs(arbitragePairs)
                            .paycheck12Months(calendar)
                            .benchmarks(benchmarks)
                            .build();
                });
    }

    private Mono<List<ReportMarketSnapshotDto.StockReportItemDto>> fetchLiveStocks() {
        String sql = """
            SELECT 
                s.code, s.org_name_ru, s.price, s.prev_close_price, s.currency_type, s.sec_type,
                t.change_val, t.trend, t.high, t.low, t.volkzt
            FROM security_instrument s
            LEFT JOIN ticker t ON UPPER(s.code) = UPPER(t.ticker)
            WHERE s.sec_type IN ('share', 'stock') OR s.code ~ '^[A-Z]{4}$'
            ORDER BY COALESCE(s.volkzt, 0) DESC
            LIMIT 100
        """;

        return databaseClient.sql(sql)
                .map((row, meta) -> {
                    String code = row.get("code", String.class);
                    String name = row.get("org_name_ru", String.class);
                    BigDecimal price = row.get("price", BigDecimal.class);
                    BigDecimal prevClose = row.get("prev_close_price", BigDecimal.class);
                    String currency = row.get("currency_type", String.class);
                    BigDecimal change = row.get("change_val", BigDecimal.class);

                    BigDecimal dayChangePct = BigDecimal.ZERO;
                    if (change != null) {
                        dayChangePct = change;
                    } else if (price != null && prevClose != null && prevClose.compareTo(BigDecimal.ZERO) > 0) {
                        dayChangePct = price.subtract(prevClose).multiply(BigDecimal.valueOf(100)).divide(prevClose, 2, RoundingMode.HALF_UP);
                    }

                    return ReportMarketSnapshotDto.StockReportItemDto.builder()
                            .code(code != null ? code.toUpperCase() : "")
                            .name(name != null ? name : "")
                            .sector("акции")
                            .exchange("KASE")
                            .currentPrice(price != null ? price : BigDecimal.ZERO)
                            .currency(currency != null && !currency.isBlank() ? currency : "KZT")
                            .dayChangePct(dayChangePct)
                            .trend(dayChangePct.compareTo(BigDecimal.ZERO) >= 0 ? "Бычий 🐂" : "Медвежий 🐻")
                            .build();
                })
                .all()
                .collectList()
                .defaultIfEmpty(new ArrayList<>());
    }

    private Mono<List<ReportMarketSnapshotDto.BondReportItemDto>> fetchAllLiveBonds() {
        String sql = """
            SELECT 
                s.code, s.org_name_ru, s.org_short_name_ru, s.sec_type, s.currency_type,
                s.price, s.volume, s.volume_number, s.finish_date, s.dtm,
                COALESCE(
                    CASE WHEN s.dohod > 0 AND s.dohod < 50 THEN s.dohod END,
                    CASE WHEN s.dohod_total > 0 AND s.dohod_total < 50 THEN s.dohod_total END,
                    CASE WHEN GREATEST(COALESCE(t.cupon, 0), COALESCE(t.cupon2, 0)) > 0 AND GREATEST(COALESCE(t.cupon, 0), COALESCE(t.cupon2, 0)) < 50 
                         THEN GREATEST(COALESCE(t.cupon, 0), COALESCE(t.cupon2, 0)) END,
                    15.0
                ) AS effective_yield,
                GREATEST(COALESCE(t.cupon, 0), COALESCE(t.cupon2, 0)) AS coupon_rate,
                t.period, t.cupon_type
            FROM security_instrument s
            LEFT JOIN ticker t ON UPPER(s.code) = UPPER(t.ticker)
            WHERE (s.sec_type IN ('bond', 'gsec', 'ifo', 'mfo', 'repo') OR s.code ~ '[a-z][0-9]+$')
              AND (s.finish_date IS NULL OR s.finish_date >= CURRENT_DATE)
              AND (s.dtm IS NULL OR s.dtm > 0)
            ORDER BY effective_yield DESC
            LIMIT 1500
        """;

        return databaseClient.sql(sql)
                .map((row, meta) -> {
                    String code = row.get("code", String.class);
                    String name = row.get("org_short_name_ru", String.class);
                    if (name == null || name.isBlank()) name = row.get("org_name_ru", String.class);
                    String secType = row.get("sec_type", String.class);
                    String currency = row.get("currency_type", String.class);
                    BigDecimal price = row.get("price", BigDecimal.class);
                    BigDecimal effYield = row.get("effective_yield", BigDecimal.class);
                    BigDecimal coupon = row.get("coupon_rate", BigDecimal.class);
                    LocalDate finishDate = row.get("finish_date", LocalDate.class);
                    Integer dtm = row.get("dtm", Integer.class);
                    BigDecimal volume = row.get("volume", BigDecimal.class);
                    BigDecimal volumeNumber = row.get("volume_number", BigDecimal.class);

                    // Nominal calculation
                    BigDecimal nominal = BigDecimal.valueOf(1000);
                    if (volume != null && volumeNumber != null && volumeNumber.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal calcNom = volume.divide(volumeNumber, 0, RoundingMode.HALF_UP);
                        if (calcNom.compareTo(BigDecimal.valueOf(10)) >= 0) nominal = calcNom;
                    }

                    // Discount
                    BigDecimal discountPct = BigDecimal.ZERO;
                    if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
                        if (price.compareTo(BigDecimal.valueOf(150)) <= 0) {
                            // Price in percent (e.g. 92.5%)
                            discountPct = BigDecimal.valueOf(100).subtract(price).max(BigDecimal.ZERO);
                        } else if (nominal.compareTo(BigDecimal.ZERO) > 0) {
                            // Price in KZT
                            discountPct = nominal.subtract(price).multiply(BigDecimal.valueOf(100)).divide(nominal, 2, RoundingMode.HALF_UP).max(BigDecimal.ZERO);
                        }
                    }

                    // Duration
                    String duration = "По регламенту";
                    if (finishDate != null) {
                        long days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), finishDate);
                        if (days > 0) {
                            long years = days / 365;
                            long months = (days % 365) / 30;
                            duration = years > 0 ? years + " г. " + months + " мес." : days + " дн.";
                        }
                    }

                    // Category
                    String category = "CORPORATE";
                    if ("gsec".equalsIgnoreCase(secType) || (code != null && code.startsWith("MU"))) {
                        category = "SOVEREIGN";
                    } else if (name != null && (name.contains("Отбасы") || name.contains("Развития Казахстана") || name.contains("Самрук") || name.contains("Байтерек") || name.contains("КФУ"))) {
                        category = "QUASIGOV";
                    } else if (discountPct.compareTo(new BigDecimal("5.0")) >= 0) {
                        category = "DISCOUNT";
                    } else if ("USD".equalsIgnoreCase(currency) || "EUR".equalsIgnoreCase(currency)) {
                        category = "FOREIGN_CURRENCY";
                    }

                    return ReportMarketSnapshotDto.BondReportItemDto.builder()
                            .code(code != null ? code.toUpperCase() : "")
                            .name(name != null ? name : "")
                            .category(category)
                            .currency(currency != null && !currency.isBlank() ? currency : "KZT")
                            .ytm(effYield != null ? effYield.setScale(2, RoundingMode.HALF_UP) : new BigDecimal("15.00"))
                            .coupon(coupon != null ? coupon.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                            .couponFrequency("Полугодовой")
                            .finishDate(finishDate)
                            .durationFormatted(duration)
                            .nominal(nominal)
                            .currentPrice(price != null ? price : nominal)
                            .discountPct(discountPct.setScale(2, RoundingMode.HALF_UP))
                            .isTaxExempt(true)
                            .build();
                })
                .all()
                .collectList()
                .defaultIfEmpty(new ArrayList<>());
    }

    private List<ReportMarketSnapshotDto.CouponMonthDto> build12MonthCalendar(BigDecimal capital) {
        String[] monthNames = {"Янв", "Фев", "Мар", "Апр", "Май", "Июн", "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек"};
        String[] issuers = {"Отбасы Банк", "Казахстанский фонд устойчивости (КФУ)", "Банк Развития Казахстана (БРК)", "Самрук-Қазына"};
        String[] codes = {"JSBNb13", "KFUSb35", "BRKZb18", "SKKZb23"};

        List<ReportMarketSnapshotDto.CouponMonthDto> list = new ArrayList<>();
        // Monthly payout ~ capital * (17.4% / 12)
        BigDecimal monthlyPayout = capital.multiply(new BigDecimal("0.1745")).divide(BigDecimal.valueOf(12), 0, RoundingMode.HALF_UP);

        for (int i = 0; i < 12; i++) {
            list.add(ReportMarketSnapshotDto.CouponMonthDto.builder()
                    .monthNumber(i + 1)
                    .monthName(monthNames[i])
                    .issuerCode(codes[i % codes.length])
                    .issuerName(issuers[i % issuers.length])
                    .payoutAmount(monthlyPayout)
                    .build());
        }
        return list;
    }
}
