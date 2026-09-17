package kz.nurgissa.kasestockexchangeparser.service.impl;

import kz.nurgissa.kasestockexchangeparser.model.dtos.*;
import kz.nurgissa.kasestockexchangeparser.model.entities.MarketMakerEntity;
import kz.nurgissa.kasestockexchangeparser.repositories.*;
import kz.nurgissa.kasestockexchangeparser.service.BondAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultBondAnalyticsService implements BondAnalyticsService {

    private final DatabaseClient databaseClient;
    private final SecurityInstrumentRepository securityInstrumentRepository;
    private final TickerRepository tickerRepository;
    private final MarketMakerRepository marketMakerRepository;
    private final SecurityPriceHistoryRepository priceHistoryRepository;

    private static final String EFFECTIVE_YIELD_SQL = """
        COALESCE(
            CASE WHEN s.dohod > 0 AND s.dohod < 50 THEN s.dohod END,
            CASE WHEN s.dohod_total > 0 AND s.dohod_total < 50 THEN s.dohod_total END,
            CASE WHEN GREATEST(COALESCE(t.cupon, 0), COALESCE(t.cupon2, 0)) > 0 AND GREATEST(COALESCE(t.cupon, 0), COALESCE(t.cupon2, 0)) < 50 
                 THEN GREATEST(COALESCE(t.cupon, 0), COALESCE(t.cupon2, 0)) END
        )
    """;

    @Override
    public Mono<List<BondItemDto>> getBondScreener(
            String currency,
            Double minYtm,
            Double maxYtm,
            Integer minDtm,
            Integer maxDtm,
            Double maxSpread,
            String preset,
            String search,
            String sortBy,
            String sortDir,
            Integer limit,
            Integer offset
    ) {
        StringBuilder sql = new StringBuilder("""
            SELECT
                s.id, s.code, s.sec_type, s.org_code, s.org_name_ru, s.org_short_name_ru,
                s.price, s.close_price, s.best_bid, s.best_offer,
                COALESCE(s.ytm, ROUND(s.dtm::numeric / 365.25, 1)) AS years_to_mat,
                COALESCE(
                    CASE WHEN s.dohod > 0 AND s.dohod < 50 THEN s.dohod END,
                    CASE WHEN s.dohod_total > 0 AND s.dohod_total < 50 THEN s.dohod_total END,
                    CASE WHEN GREATEST(COALESCE(t.cupon, 0), COALESCE(t.cupon2, 0)) > 0 AND GREATEST(COALESCE(t.cupon, 0), COALESCE(t.cupon2, 0)) < 50 
                         THEN GREATEST(COALESCE(t.cupon, 0), COALESCE(t.cupon2, 0)) END
                ) AS effective_yield,
                s.dohod, s.dtm, s.volkzt, s.volusd, s.dealcnt,
                s.monthly_spark_line,
                GREATEST(COALESCE(t.cupon, 0), COALESCE(t.cupon2, 0)) AS cupon,
                COALESCE(t.finish_date, s.repayment_start_date) AS finish_date,
                COALESCE(NULLIF(t.currency, ''), NULLIF(s.currency_type, ''), 'KZT') AS currency,
                CASE WHEN s.volume_number > 0 AND s.volume > 0 THEN ROUND(s.volume / s.volume_number, 0) ELSE 1000 END AS face_value,
                (SELECT COUNT(*) FROM instrument_market_maker imm WHERE imm.security_instrument_id = s.id) AS mm_count
            FROM security_instrument s
            LEFT JOIN ticker t ON s.id = t.security_instrument_id
            WHERE s.sec_type IN ('bond', 'gsec')
              AND (COALESCE(s.dtm, 0) > 0 OR COALESCE(t.finish_date, s.repayment_start_date) >= CURRENT_DATE)
        """);

        if (currency != null && !currency.isBlank()) {
            sql.append(" AND COALESCE(NULLIF(t.currency, ''), NULLIF(s.currency_type, ''), 'KZT') = '").append(currency.replace("'", "")).append("'");
        }
        if (minYtm != null) {
            sql.append(" AND ").append(EFFECTIVE_YIELD_SQL).append(" >= ").append(minYtm);
        }
        if (maxYtm != null) {
            sql.append(" AND ").append(EFFECTIVE_YIELD_SQL).append(" <= ").append(maxYtm);
        }
        if (minDtm != null) {
            sql.append(" AND s.dtm >= ").append(minDtm);
        }
        if (maxDtm != null) {
            sql.append(" AND s.dtm <= ").append(maxDtm);
        }
        if (search != null && !search.isBlank()) {
            String safeSearch = search.replace("'", "").toLowerCase();
            sql.append(" AND (LOWER(s.code) LIKE '%").append(safeSearch)
               .append("%' OR LOWER(s.org_name_ru) LIKE '%").append(safeSearch)
               .append("%' OR LOWER(s.org_short_name_ru) LIKE '%").append(safeSearch).append("%')");
        }

        // Presets logic
        if ("deposit_replacement".equalsIgnoreCase(preset)) {
            sql.append(" AND ").append(EFFECTIVE_YIELD_SQL).append(" >= 14.0 AND s.dtm > 0 AND s.dtm <= 730 AND (s.sec_type = 'gsec' OR s.code LIKE 'SKKZ%' OR s.code LIKE 'BRKZ%' OR s.code LIKE 'KFUS%' OR s.code LIKE 'JSBN%' OR s.best_bid > 0)");
        } else if ("high_yield".equalsIgnoreCase(preset)) {
            sql.append(" AND ").append(EFFECTIVE_YIELD_SQL).append(" >= 18.0");
        } else if ("most_liquid".equalsIgnoreCase(preset)) {
            sql.append(" AND s.best_bid > 0 AND s.best_offer > 0 AND s.volkzt > 0");
        } else if ("short_term".equalsIgnoreCase(preset)) {
            sql.append(" AND s.dtm > 0 AND s.dtm <= 365");
        } else if ("sovereign".equalsIgnoreCase(preset)) {
            sql.append(" AND s.sec_type = 'gsec'");
        }

        sql.append(" ORDER BY ");
        String direction = "asc".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";

        if ("spread".equalsIgnoreCase(sortBy)) {
            sql.append("CASE WHEN s.best_offer > 0 AND s.best_bid > 0 THEN (s.best_offer - s.best_bid) ELSE 9999 END ").append(direction);
        } else if ("volume".equalsIgnoreCase(sortBy)) {
            sql.append("s.volkzt ").append(direction).append(" NULLS LAST");
        } else if ("dtm".equalsIgnoreCase(sortBy)) {
            sql.append("s.dtm ").append(direction).append(" NULLS LAST");
        } else if ("price".equalsIgnoreCase(sortBy)) {
            sql.append("s.price ").append(direction).append(" NULLS LAST");
        } else {
            // Default sort by real effective yield DESC
            sql.append(EFFECTIVE_YIELD_SQL).append(" ").append(direction).append(" NULLS LAST, s.volkzt DESC NULLS LAST");
        }

        int lim = (limit != null && limit > 0) ? Math.min(limit, 500) : 100;
        int off = (offset != null && offset >= 0) ? offset : 0;
        sql.append(" LIMIT ").append(lim).append(" OFFSET ").append(off);

        return databaseClient.sql(sql.toString())
                .map((row, metadata) -> {
                    BigDecimal bestBid = row.get("best_bid", BigDecimal.class);
                    BigDecimal bestOffer = row.get("best_offer", BigDecimal.class);
                    BigDecimal spread = null;
                    BigDecimal spreadPercent = null;

                    if (bestBid != null && bestOffer != null && bestBid.compareTo(BigDecimal.ZERO) > 0 && bestOffer.compareTo(BigDecimal.ZERO) > 0) {
                        spread = bestOffer.subtract(bestBid);
                        BigDecimal mid = bestOffer.add(bestBid).divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP);
                        if (mid.compareTo(BigDecimal.ZERO) > 0) {
                            spreadPercent = spread.multiply(BigDecimal.valueOf(100)).divide(mid, 2, RoundingMode.HALF_UP);
                        }
                    }

                    BigDecimal volkzt = row.get("volkzt", BigDecimal.class);
                    Integer dealcnt = row.get("dealcnt", Integer.class);
                    Long mmCountLong = row.get("mm_count", Long.class);
                    int mmCount = mmCountLong != null ? mmCountLong.intValue() : 0;

                    int liquidityScore = calculateLiquidityScore(spreadPercent, volkzt, dealcnt, mmCount);
                    String liquidityClass = getLiquidityClass(liquidityScore);

                    String sparklineStr = row.get("monthly_spark_line", String.class);
                    List<BigDecimal> sparklinePoints = parseSparkline(sparklineStr);

                    BigDecimal effectiveYield = row.get("effective_yield", BigDecimal.class);
                    BigDecimal yearsToMat = row.get("years_to_mat", BigDecimal.class);
                    Integer dtm = row.get("dtm", Integer.class);
                    String secType = row.get("sec_type", String.class);
                    String curr = row.get("currency", String.class);
                    BigDecimal faceVal = row.get("face_value", BigDecimal.class);
                    BigDecimal gSpread = calculateGSpread(effectiveYield, dtm, secType, curr);

                    return BondItemDto.builder()
                            .id(row.get("id", Long.class))
                            .code(row.get("code", String.class))
                            .secType(secType)
                            .orgCode(row.get("org_code", String.class))
                            .orgNameRu(row.get("org_name_ru", String.class))
                            .orgShortNameRu(row.get("org_short_name_ru", String.class))
                            .price(row.get("price", BigDecimal.class))
                            .closePrice(row.get("close_price", BigDecimal.class))
                            .bestBid(bestBid)
                            .bestOffer(bestOffer)
                            .spread(spread)
                            .spreadPercent(spreadPercent)
                            .ytm(effectiveYield)
                            .yearsToMaturity(yearsToMat)
                            .dohod(row.get("dohod", BigDecimal.class))
                            .dtm(dtm)
                            .cupon(row.get("cupon", BigDecimal.class))
                            .finishDate(row.get("finish_date", LocalDate.class))
                            .currency(curr)
                            .faceValue(faceVal)
                            .volkzt(volkzt)
                            .volusd(row.get("volusd", BigDecimal.class))
                            .dealcnt(dealcnt)
                            .monthlySparkLine(sparklineStr)
                            .sparklinePoints(sparklinePoints)
                            .marketMakersCount(mmCount)
                            .gSpread(gSpread)
                            .liquidityScore(liquidityScore)
                            .liquidityClass(liquidityClass)
                            .build();
                })
                .all()
                .filter(item -> maxSpread == null || item.getSpreadPercent() == null || item.getSpreadPercent().doubleValue() <= maxSpread)
                .collectList();
    }

    @Override
    public Mono<YieldCurveResponseDto> getYieldCurve(String currency) {
        String curr = (currency == null || currency.isBlank()) ? "KZT" : currency.replace("'", "");
        String sql = """
            SELECT s.id, s.code, s.org_name_ru, s.sec_type, s.dtm, s.volkzt, s.best_bid, s.best_offer,
                   COALESCE(s.ytm, ROUND(s.dtm::numeric / 365.25, 2)) AS years_to_maturity,
                   COALESCE(
                       CASE WHEN s.dohod > 0 AND s.dohod < 50 THEN s.dohod END,
                       CASE WHEN s.dohod_total > 0 AND s.dohod_total < 50 THEN s.dohod_total END,
                       CASE WHEN GREATEST(COALESCE(t.cupon, 0), COALESCE(t.cupon2, 0)) > 0 AND GREATEST(COALESCE(t.cupon, 0), COALESCE(t.cupon2, 0)) < 50 
                            THEN GREATEST(COALESCE(t.cupon, 0), COALESCE(t.cupon2, 0)) END
                   ) AS effective_yield,
                   COALESCE(NULLIF(t.currency, ''), NULLIF(s.currency_type, ''), 'KZT') AS currency
            FROM security_instrument s
            LEFT JOIN ticker t ON s.id = t.security_instrument_id
            WHERE s.sec_type IN ('gsec', 'bond')
              AND s.dtm IS NOT NULL AND s.dtm > 0 AND s.dtm <= 10950
              AND COALESCE(NULLIF(t.currency, ''), NULLIF(s.currency_type, ''), 'KZT') = '%s'
              AND (
                  (s.sec_type = 'gsec' AND s.dohod >= 8.0) OR
                  (s.sec_type = 'bond' AND (
                      (s.dohod > 0 AND s.dohod < 50) OR 
                      (s.dohod_total > 0 AND s.dohod_total < 50) OR 
                      (GREATEST(COALESCE(t.cupon, 0), COALESCE(t.cupon2, 0)) > 0 AND GREATEST(COALESCE(t.cupon, 0), COALESCE(t.cupon2, 0)) < 50)
                  ))
              )
            ORDER BY s.dtm ASC
        """.formatted(curr);

        return databaseClient.sql(sql)
                .map((row, metadata) -> {
                    BigDecimal bestBid = row.get("best_bid", BigDecimal.class);
                    BigDecimal bestOffer = row.get("best_offer", BigDecimal.class);
                    BigDecimal spreadPercent = null;
                    if (bestBid != null && bestOffer != null && bestBid.compareTo(BigDecimal.ZERO) > 0 && bestOffer.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal spread = bestOffer.subtract(bestBid);
                        BigDecimal mid = bestOffer.add(bestBid).divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP);
                        if (mid.compareTo(BigDecimal.ZERO) > 0) {
                            spreadPercent = spread.multiply(BigDecimal.valueOf(100)).divide(mid, 2, RoundingMode.HALF_UP);
                        }
                    }

                    Integer dtm = row.get("dtm", Integer.class);
                    BigDecimal years = row.get("years_to_maturity", BigDecimal.class);
                    BigDecimal effectiveYield = row.get("effective_yield", BigDecimal.class);
                    String secType = row.get("sec_type", String.class);
                    String c = row.get("currency", String.class);

                    return YieldCurveResponseDto.CurvePointDto.builder()
                            .id(row.get("id", Long.class))
                            .code(row.get("code", String.class))
                            .name(row.get("org_name_ru", String.class))
                            .secType(secType)
                            .dtm(dtm)
                            .yearsToMaturity(years != null ? years.doubleValue() : null)
                            .ytm(effectiveYield)
                            .gSpread(calculateGSpread(effectiveYield, dtm, secType, c))
                            .spreadPercent(spreadPercent)
                            .volumeKzt(row.get("volkzt", BigDecimal.class))
                            .currency(c)
                            .build();
                })
                .all()
                .collectList()
                .map(points -> {
                    List<YieldCurveResponseDto.CurvePointDto> sovereign = points.stream()
                            .filter(p -> "gsec".equalsIgnoreCase(p.getSecType()))
                            .collect(Collectors.toList());

                    List<YieldCurveResponseDto.CurvePointDto> corporate = points.stream()
                            .filter(p -> "bond".equalsIgnoreCase(p.getSecType()))
                            .collect(Collectors.toList());

                    YieldCurveResponseDto.BenchmarkRatesDto benchmarks = computeBenchmarks(sovereign, points);

                    return YieldCurveResponseDto.builder()
                            .sovereignCurve(sovereign)
                            .corporatePoints(corporate)
                            .benchmarks(benchmarks)
                            .build();
                });
    }

    @Override
    public Mono<MarketSummaryDto> getMarketSummary() {
        String countSql = """
            SELECT
                COUNT(*) AS total_count,
                COUNT(CASE WHEN volkzt > 0 OR dealcnt > 0 THEN 1 END) AS active_count,
                COALESCE(SUM(volkzt), 0) AS total_vol_kzt,
                COALESCE(SUM(volusd), 0) AS total_vol_usd,
                AVG(CASE WHEN best_offer > 0 AND best_bid > 0 THEN ((best_offer - best_bid) / ((best_offer + best_bid)/2)) * 100 END) AS avg_spread
            FROM security_instrument
        """;

        String topVolumeSql = """
            SELECT s.id, s.code, s.org_name_ru, s.sec_type, s.price,
                   COALESCE(
                       CASE WHEN s.dohod > 0 AND s.dohod < 50 THEN s.dohod END,
                       CASE WHEN s.dohod_total > 0 AND s.dohod_total < 50 THEN s.dohod_total END,
                       CASE WHEN GREATEST(COALESCE(t.cupon, 0), COALESCE(t.cupon2, 0)) > 0 AND GREATEST(COALESCE(t.cupon, 0), COALESCE(t.cupon2, 0)) < 50 
                            THEN GREATEST(COALESCE(t.cupon, 0), COALESCE(t.cupon2, 0)) END
                   ) AS ytm,
                   s.volkzt, s.trand_percent,
                   CASE WHEN s.best_offer > 0 AND s.best_bid > 0 THEN ((s.best_offer - s.best_bid) / ((s.best_offer + s.best_bid)/2)) * 100 END AS spread_pct
            FROM security_instrument s
            LEFT JOIN ticker t ON s.id = t.security_instrument_id
            WHERE s.volkzt > 0
            ORDER BY s.volkzt DESC
            LIMIT 5
        """;

        String topYieldSql = """
            SELECT s.id, s.code, s.org_name_ru, s.sec_type, s.price,
                   COALESCE(
                       CASE WHEN s.dohod > 0 AND s.dohod < 50 THEN s.dohod END,
                       CASE WHEN s.dohod_total > 0 AND s.dohod_total < 50 THEN s.dohod_total END,
                       CASE WHEN GREATEST(COALESCE(t.cupon, 0), COALESCE(t.cupon2, 0)) > 0 AND GREATEST(COALESCE(t.cupon, 0), COALESCE(t.cupon2, 0)) < 50 
                            THEN GREATEST(COALESCE(t.cupon, 0), COALESCE(t.cupon2, 0)) END
                   ) AS ytm,
                   s.volkzt, s.trand_percent,
                   CASE WHEN s.best_offer > 0 AND s.best_bid > 0 THEN ((s.best_offer - s.best_bid) / ((s.best_offer + s.best_bid)/2)) * 100 END AS spread_pct
            FROM security_instrument s
            LEFT JOIN ticker t ON s.id = t.security_instrument_id
            WHERE s.sec_type IN ('bond', 'gsec')
              AND (COALESCE(s.dtm, 0) > 0 OR COALESCE(t.finish_date, s.repayment_start_date) >= CURRENT_DATE)
              AND (
                  (s.dohod > 0 AND s.dohod < 50) OR 
                  (s.dohod_total > 0 AND s.dohod_total < 50) OR 
                  (GREATEST(COALESCE(t.cupon, 0), COALESCE(t.cupon2, 0)) > 0 AND GREATEST(COALESCE(t.cupon, 0), COALESCE(t.cupon2, 0)) < 50)
              )
            ORDER BY ytm DESC NULLS LAST
            LIMIT 5
        """;

        return databaseClient.sql(countSql)
                .map((row, metadata) -> Map.of(
                        "totalCount", row.get("total_count", Long.class),
                        "activeCount", row.get("active_count", Long.class),
                        "totalVolKzt", Optional.ofNullable(row.get("total_vol_kzt", BigDecimal.class)).orElse(BigDecimal.ZERO),
                        "totalVolUsd", Optional.ofNullable(row.get("total_vol_usd", BigDecimal.class)).orElse(BigDecimal.ZERO),
                        "avgSpread", Optional.ofNullable(row.get("avg_spread", BigDecimal.class)).orElse(BigDecimal.ZERO)
                ))
                .one()
                .flatMap(stats -> {
                    Mono<List<MarketSummaryDto.TopInstrumentDto>> topVolMono = databaseClient.sql(topVolumeSql)
                            .map(this::mapToTopInstrument)
                            .all()
                            .collectList();

                    Mono<List<MarketSummaryDto.TopInstrumentDto>> topYieldMono = databaseClient.sql(topYieldSql)
                            .map(this::mapToTopInstrument)
                            .all()
                            .collectList();

                    return Mono.zip(topVolMono, topYieldMono)
                            .map(tuple -> {
                                List<MarketSummaryDto.TopInstrumentDto> topVol = tuple.getT1();
                                List<MarketSummaryDto.TopInstrumentDto> topYield = tuple.getT2();

                                List<MarketSummaryDto.AnomalyItemDto> anomalies = new ArrayList<>();
                                for (MarketSummaryDto.TopInstrumentDto item : topVol) {
                                    if (item.getVolumeKzt() != null && item.getVolumeKzt().compareTo(BigDecimal.valueOf(1_000_000_000L)) > 0) {
                                        anomalies.add(MarketSummaryDto.AnomalyItemDto.builder()
                                                .id(item.getId())
                                                .code(item.getCode())
                                                .name(item.getName())
                                                .type("VOLUME_SPIKE")
                                                .description("Крупная институциональная активность: дневной объем > 1 млрд ₸")
                                                .metricValue(item.getVolumeKzt())
                                                .build());
                                    }
                                }

                                return MarketSummaryDto.builder()
                                        .totalInstruments((Long) stats.get("totalCount"))
                                        .totalActiveTraded((Long) stats.get("activeCount"))
                                        .totalVolumeKzt((BigDecimal) stats.get("totalVolKzt"))
                                        .totalVolumeUsd((BigDecimal) stats.get("totalVolUsd"))
                                        .averageSpreadPercent(((BigDecimal) stats.get("avgSpread")).setScale(2, RoundingMode.HALF_UP))
                                        .topVolume(topVol)
                                        .topYieldBonds(topYield)
                                        .recentAnomalies(anomalies)
                                        .build();
                            });
                });
    }

    @Override
    public Mono<InstrumentDetailDto> getInstrumentDetail(Long id) {
        return securityInstrumentRepository.findById(id)
                .flatMap(instr -> {
                    Mono<kz.nurgissa.kasestockexchangeparser.model.entities.TickerEntity> tickerMono =
                            tickerRepository.findById(id).defaultIfEmpty(new kz.nurgissa.kasestockexchangeparser.model.entities.TickerEntity());

                    String mmSql = """
                        SELECT mm.* FROM market_maker mm
                        JOIN instrument_market_maker imm ON mm.org_code = imm.org_code
                        WHERE imm.security_instrument_id = :id
                    """;
                    Mono<List<MarketMakerEntity>> mmMono = databaseClient.sql(mmSql)
                            .bind("id", id)
                            .map((row, meta) -> MarketMakerEntity.builder()
                                     .orgCode(row.get("org_code", String.class))
                                     .orgNameRu(row.get("org_name_ru", String.class))
                                     .orgNameEn(row.get("org_name_en", String.class))
                                     .orgNameKz(row.get("org_name_kz", String.class))
                                     .orgShortNameRu(row.get("org_short_name_ru", String.class))
                                     .build())
                            .all()
                            .collectList();

                    Mono<List<kz.nurgissa.kasestockexchangeparser.model.entities.SecurityPriceHistoryEntity>> historyMono =
                            priceHistoryRepository.findRecentByInstrumentId(id, 20).collectList();

                    return Mono.zip(tickerMono, mmMono, historyMono)
                            .map(tuple -> {
                                var ticker = tuple.getT1().getSecurityInstrumentId() != null ? tuple.getT1() : null;
                                var makers = tuple.getT2();
                                var history = tuple.getT3();

                                BigDecimal spread = null;
                                BigDecimal spreadPercent = null;
                                if (instr.getBestOffer() != null && instr.getBestBid() != null &&
                                    instr.getBestOffer().compareTo(BigDecimal.ZERO) > 0 && instr.getBestBid().compareTo(BigDecimal.ZERO) > 0) {
                                    spread = instr.getBestOffer().subtract(instr.getBestBid());
                                    BigDecimal mid = instr.getBestOffer().add(instr.getBestBid()).divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP);
                                    if (mid.compareTo(BigDecimal.ZERO) > 0) {
                                        spreadPercent = spread.multiply(BigDecimal.valueOf(100)).divide(mid, 2, RoundingMode.HALF_UP);
                                    }
                                }

                                int score = calculateLiquidityScore(spreadPercent, instr.getVolkzt(), instr.getDealcnt(), makers.size());
                                
                                BigDecimal couponVal = null;
                                if (ticker != null) {
                                    BigDecimal c1 = ticker.getCupon();
                                    BigDecimal c2 = ticker.getCupon2();
                                    if (c1 != null && c2 != null) {
                                        couponVal = c1.max(c2);
                                    } else if (c1 != null) {
                                        couponVal = c1;
                                    } else if (c2 != null) {
                                        couponVal = c2;
                                    }
                                }

                                BigDecimal effYield = calculateEffectiveYield(instr.getDohod(), instr.getDohodTotal(), couponVal);
                                BigDecimal yearsToMat = instr.getYtm() != null && instr.getYtm().compareTo(BigDecimal.ZERO) > 0 && instr.getYtm().compareTo(BigDecimal.valueOf(100)) <= 0
                                        ? instr.getYtm() 
                                        : (instr.getDtm() != null ? BigDecimal.valueOf(Math.round(instr.getDtm() / 365.25 * 10.0) / 10.0) : null);

                                BigDecimal faceVal = (instr.getVolume() != null && instr.getVolumeNumber() != null && instr.getVolumeNumber().compareTo(BigDecimal.ZERO) > 0)
                                        ? instr.getVolume().divide(instr.getVolumeNumber(), 0, RoundingMode.HALF_UP)
                                        : BigDecimal.valueOf(1000);

                                String resCurr = (ticker != null && ticker.getCurrency() != null && !ticker.getCurrency().isBlank())
                                        ? ticker.getCurrency()
                                        : ((instr.getCurrencyType() != null && !instr.getCurrencyType().isBlank()) ? instr.getCurrencyType() : "KZT");

                                return InstrumentDetailDto.builder()
                                        .instrument(instr)
                                        .ticker(ticker)
                                        .marketMakers(makers)
                                        .priceHistory(history)
                                        .spread(spread)
                                        .spreadPercent(spreadPercent)
                                        .liquidityScore(score)
                                        .sparklinePoints(parseSparkline(instr.getMonthlySparkLine()))
                                        .effectiveYield(effYield)
                                        .yearsToMaturity(yearsToMat)
                                        .faceValue(faceVal)
                                        .resolvedCurrency(resCurr)
                                        .build();
                            });
                });
    }

    private BigDecimal calculateEffectiveYield(BigDecimal dohod, BigDecimal dohodTotal, BigDecimal cupon) {
        if (dohod != null && dohod.compareTo(BigDecimal.ZERO) > 0 && dohod.compareTo(BigDecimal.valueOf(50)) < 0) {
            return dohod;
        }
        if (dohodTotal != null && dohodTotal.compareTo(BigDecimal.ZERO) > 0 && dohodTotal.compareTo(BigDecimal.valueOf(50)) < 0) {
            return dohodTotal;
        }
        if (cupon != null && cupon.compareTo(BigDecimal.ZERO) > 0 && cupon.compareTo(BigDecimal.valueOf(50)) < 0) {
            return cupon;
        }
        return null;
    }

    private MarketSummaryDto.TopInstrumentDto mapToTopInstrument(io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata meta) {
        BigDecimal spreadPct = row.get("spread_pct", BigDecimal.class);
        return MarketSummaryDto.TopInstrumentDto.builder()
                .id(row.get("id", Long.class))
                .code(row.get("code", String.class))
                .name(row.get("org_name_ru", String.class))
                .secType(row.get("sec_type", String.class))
                .price(row.get("price", BigDecimal.class))
                .ytm(row.get("ytm", BigDecimal.class))
                .volumeKzt(row.get("volkzt", BigDecimal.class))
                .spreadPercent(spreadPct != null ? spreadPct.setScale(2, RoundingMode.HALF_UP) : null)
                .trandPercent(row.get("trand_percent", BigDecimal.class))
                .build();
    }

    private int calculateLiquidityScore(BigDecimal spreadPercent, BigDecimal volumeKzt, Integer dealcnt, int mmCount) {
        int score = 0;
        if (spreadPercent != null) {
            double s = spreadPercent.doubleValue();
            if (s <= 0.5) score += 40;
            else if (s <= 1.5) score += 30;
            else if (s <= 3.0) score += 20;
            else if (s <= 5.0) score += 10;
        }

        if (volumeKzt != null) {
            double v = volumeKzt.doubleValue();
            if (v >= 100_000_000) score += 30;
            else if (v >= 10_000_000) score += 20;
            else if (v >= 1_000_000) score += 10;
            else if (v > 0) score += 5;
        }

        if (dealcnt != null) {
            if (dealcnt >= 10) score += 20;
            else if (dealcnt >= 1) score += 10;
        }

        if (mmCount >= 2) score += 10;
        else if (mmCount == 1) score += 5;

        return Math.min(100, score);
    }

    private String getLiquidityClass(int score) {
        if (score >= 70) return "HIGH";
        if (score >= 40) return "MEDIUM";
        if (score >= 15) return "LOW";
        return "ILLIQUID";
    }

    private BigDecimal calculateGSpread(BigDecimal ytm, Integer dtm, String secType, String currency) {
        if (ytm == null || dtm == null || dtm <= 0 || "gsec".equalsIgnoreCase(secType)) return null;
        if (currency != null && !"KZT".equalsIgnoreCase(currency)) return null;

        // Рыночная кривая доходностей ГЦБ Минфина РК (МЕКАМ/МЕУКАМ) по срочности:
        double benchmark;
        double years = dtm / 365.25;
        if (years <= 1.0) benchmark = 16.0;
        else if (years <= 2.0) benchmark = 15.5;
        else if (years <= 3.0) benchmark = 14.8;
        else if (years <= 5.0) benchmark = 14.2;
        else benchmark = 13.5;

        double gSpread = ytm.doubleValue() - benchmark;
        return BigDecimal.valueOf(Math.round(gSpread * 100.0) / 100.0);
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

    private YieldCurveResponseDto.BenchmarkRatesDto computeBenchmarks(
            List<YieldCurveResponseDto.CurvePointDto> sovereign,
            List<YieldCurveResponseDto.CurvePointDto> all
    ) {
        BigDecimal y1 = findClosestYtm(sovereign, 365);
        BigDecimal y3 = findClosestYtm(sovereign, 365 * 3);
        BigDecimal y5 = findClosestYtm(sovereign, 365 * 5);
        BigDecimal y10 = findClosestYtm(sovereign, 365 * 10);

        List<BigDecimal> ytms = all.stream().map(YieldCurveResponseDto.CurvePointDto::getYtm).filter(Objects::nonNull).sorted().toList();
        BigDecimal median = ytms.isEmpty() ? BigDecimal.ZERO : ytms.get(ytms.size() / 2);

        return YieldCurveResponseDto.BenchmarkRatesDto.builder()
                .rate1Year(y1 != null ? y1 : BigDecimal.valueOf(14.0))
                .rate3Year(y3 != null ? y3 : BigDecimal.valueOf(13.5))
                .rate5Year(y5 != null ? y5 : BigDecimal.valueOf(13.0))
                .rate10Year(y10 != null ? y10 : BigDecimal.valueOf(12.5))
                .medianYtm(median)
                .totalBondsCount(all.size())
                .build();
    }

    private BigDecimal findClosestYtm(List<YieldCurveResponseDto.CurvePointDto> sovereign, int targetDtm) {
        return sovereign.stream()
                .min(Comparator.comparingInt(p -> Math.abs(p.getDtm() - targetDtm)))
                .map(YieldCurveResponseDto.CurvePointDto::getYtm)
                .orElse(null);
    }

    @Override
    public Mono<InstrumentDetailDto> getInstrumentDetailByCode(String code) {
        if (code == null || code.isBlank()) {
            return Mono.empty();
        }
        return securityInstrumentRepository.findByCode(code.trim().toUpperCase())
                .flatMap(instr -> getInstrumentDetail(instr.getId()));
    }

    @Override
    public Mono<List<BondItemDto>> getDiscountBonds(Double maxPricePercent) {
        double threshold = (maxPricePercent != null && maxPricePercent > 0) ? maxPricePercent : 95.0;
        return getBondScreener(null, null, null, 1, null, null, null, null, "price", "asc", 50, 0)
                .map(list -> list.stream()
                        .filter(b -> b.getPrice() != null && b.getPrice().doubleValue() <= threshold && b.getPrice().doubleValue() >= 40.0)
                        .collect(Collectors.toList())
                );
    }

    @Override
    public Mono<List<StockItemDto>> getTopStocks(List<String> specificTickers) {
        String sql = """
            SELECT s.id, s.code, COALESCE(s.org_short_name_ru, s.org_name_ru) AS name,
                   s.price, s.close_price, s.trand AS change, s.trand_percent AS change_percent,
                   s.volkzt, s.dealcnt,
                   COALESCE(NULLIF(t.currency, ''), NULLIF(s.currency_type, ''), 'KZT') AS currency
            FROM security_instrument s
            LEFT JOIN ticker t ON s.id = t.security_instrument_id
            WHERE s.sec_type IN ('share', 'stock')
            ORDER BY s.volkzt DESC NULLS LAST
            LIMIT 50
        """;
        return databaseClient.sql(sql)
                .map((row, meta) -> StockItemDto.builder()
                        .code(row.get("code", String.class))
                        .name(row.get("name", String.class))
                        .price(row.get("price", BigDecimal.class))
                        .closePrice(row.get("close_price", BigDecimal.class))
                        .change(row.get("change", BigDecimal.class))
                        .changePercent(row.get("change_percent", BigDecimal.class))
                        .volumeKzt(row.get("volkzt", BigDecimal.class))
                        .dealCount(row.get("dealcnt", Integer.class))
                        .currency(row.get("currency", String.class))
                        .build()
                )
                .all()
                .collectList()
                .map(list -> {
                    if (specificTickers != null && !specificTickers.isEmpty()) {
                        Set<String> set = specificTickers.stream().map(String::toUpperCase).collect(Collectors.toSet());
                        return list.stream().filter(s -> set.contains(s.getCode().toUpperCase())).collect(Collectors.toList());
                    }
                    return list;
                });
    }

    @Override
    public Mono<List<BondItemDto>> getUpcomingCouponBonds() {
        return getBondScreener(null, null, null, 1, 30, null, null, null, "dtm", "asc", 10, 0);
    }
}
