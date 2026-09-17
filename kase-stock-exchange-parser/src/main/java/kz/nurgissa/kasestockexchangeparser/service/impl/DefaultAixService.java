package kz.nurgissa.kasestockexchangeparser.service.impl;

import kz.nurgissa.kasestockexchangeparser.client.AixClient;
import kz.nurgissa.kasestockexchangeparser.model.dtos.AixInstrumentDto;
import kz.nurgissa.kasestockexchangeparser.model.dtos.AixMarketDepthDto;
import kz.nurgissa.kasestockexchangeparser.model.dtos.ArbitrageItemDto;
import kz.nurgissa.kasestockexchangeparser.model.entities.AixInstrumentEntity;
import kz.nurgissa.kasestockexchangeparser.repositories.AixSecurityInstrumentRepository;
import kz.nurgissa.kasestockexchangeparser.service.AixService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultAixService implements AixService {

    private final AixClient aixClient;
    private final AixSecurityInstrumentRepository aixRepository;
    private final DatabaseClient databaseClient;
    private final R2dbcEntityTemplate template;

    @Override
    public Mono<Void> fetchAndSaveAll() {
        return aixClient.fetchAllSecurityInstruments()
                .flatMap(this::saveInstrument)
                .then(
                        aixClient.fetchEtfRecords()
                                .flatMap(this::updateEtfNav)
                                .then()
                )
                .doOnSuccess(v -> log.info("AIX instruments synchronization successfully finished."))
                .doOnError(e -> log.error("Failed to sync AIX instruments: {}", e.getMessage()));
    }

    private Mono<Void> saveInstrument(AixInstrumentDto dto) {
        if (dto.getSecCode() == null || dto.getSecCode().isBlank()) {
            return Mono.empty();
        }
        AixInstrumentEntity entity = AixInstrumentEntity.builder()
                .secCode(dto.getSecCode().trim().toUpperCase())
                .isin(dto.getIsin())
                .issuer(dto.getIssuer())
                .shortName(dto.getShortName())
                .instrument(dto.getInstrument())
                .segment(dto.getSegment())
                .assetClass(dto.getAssetClass())
                .securityGroup(dto.getSecurityGroup())
                .currency(dto.getCurrency() != null ? dto.getCurrency().trim().toUpperCase() : "KZT")
                .state(dto.getState())
                .referencePrice(dto.getReferencePrice())
                .bidPrice(dto.getBidPrice())
                .bidQty(dto.getBidQty())
                .offerPrice(dto.getOfferPrice())
                .offerQty(dto.getOfferQty())
                .lastTrade(dto.getLastTrade())
                .previousClose(dto.getPreviousClose())
                .averageWeightedPrice(dto.getAverageWeightedPrice())
                .percentChange(dto.getPercentChange())
                .priceChange(dto.getPriceChange())
                .volume(dto.getVolume())
                .value(dto.getValue())
                .numberOfTrades(dto.getNumberOfTrades() != null ? dto.getNumberOfTrades() : 0)
                .updatedAt(LocalDateTime.now())
                .build();

        return aixRepository.findBySecCode(entity.getSecCode())
                .flatMap(existing -> {
                    entity.setNav(existing.getNav());
                    entity.setNavCurrency(existing.getNavCurrency());
                    return aixRepository.save(entity);
                })
                .switchIfEmpty(Mono.defer(() -> template.insert(AixInstrumentEntity.class).using(entity)))
                .onErrorResume(DuplicateKeyException.class, ex -> Mono.empty())
                .then();
    }

    private Mono<Void> updateEtfNav(AixInstrumentDto etf) {
        if (etf.getSecCode() == null || etf.getNav() == null) {
            return Mono.empty();
        }
        BigDecimal nav = null;
        try {
            nav = new BigDecimal(etf.getNav().trim());
        } catch (Exception ignored) {}

        final BigDecimal finalNav = nav;
        return aixRepository.findBySecCode(etf.getSecCode().trim().toUpperCase())
                .flatMap(entity -> {
                    entity.setNav(finalNav);
                    entity.setNavCurrency(etf.getNavCurrency() != null ? etf.getNavCurrency() : etf.getCurrency());
                    entity.setUpdatedAt(LocalDateTime.now());
                    return aixRepository.save(entity);
                })
                .then();
    }

    @Override
    public Mono<List<AixInstrumentDto>> getInstruments(String assetClass, String currency, String search, Integer limit) {
        StringBuilder sql = new StringBuilder("""
            SELECT sec_code, isin, issuer, short_name, instrument, segment,
                   asset_class, security_group, currency, state, reference_price,
                   bid_price, bid_qty, offer_price, offer_qty, last_trade, previous_close,
                   average_weighted_price, percent_change, price_change, volume, value,
                   number_of_trades, nav, nav_currency, updated_at
            FROM aix_security_instrument
            WHERE 1=1
        """);

        if (assetClass != null && !assetClass.isBlank()) {
            sql.append(" AND asset_class = '").append(assetClass.replace("'", "")).append("'");
        }
        if (currency != null && !currency.isBlank()) {
            sql.append(" AND currency = '").append(currency.replace("'", "")).append("'");
        }
        if (search != null && !search.isBlank()) {
            String q = search.replace("'", "").toLowerCase();
            sql.append(" AND (LOWER(sec_code) LIKE '%").append(q).append("%' OR LOWER(issuer) LIKE '%").append(q).append("%' OR LOWER(isin) LIKE '%").append(q).append("%')");
        }

        sql.append(" ORDER BY COALESCE(value, volume, 0) DESC, sec_code ASC");
        int max = (limit != null && limit > 0) ? Math.min(limit, 500) : 100;
        sql.append(" LIMIT ").append(max);

        return databaseClient.sql(sql.toString())
                .map((row, meta) -> AixInstrumentDto.builder()
                        .secCode(row.get("sec_code", String.class))
                        .isin(row.get("isin", String.class))
                        .issuer(row.get("issuer", String.class))
                        .shortName(row.get("short_name", String.class))
                        .instrument(row.get("instrument", String.class))
                        .segment(row.get("segment", String.class))
                        .assetClass(row.get("asset_class", String.class))
                        .securityGroup(row.get("security_group", String.class))
                        .currency(row.get("currency", String.class))
                        .state(row.get("state", String.class))
                        .referencePrice(row.get("reference_price", BigDecimal.class))
                        .bidPrice(row.get("bid_price", BigDecimal.class))
                        .bidQty(row.get("bid_qty", Long.class))
                        .offerPrice(row.get("offer_price", BigDecimal.class))
                        .offerQty(row.get("offer_qty", Long.class))
                        .lastTrade(row.get("last_trade", BigDecimal.class))
                        .previousClose(row.get("previous_close", BigDecimal.class))
                        .averageWeightedPrice(row.get("average_weighted_price", BigDecimal.class))
                        .percentChange(row.get("percent_change", BigDecimal.class))
                        .priceChange(row.get("price_change", BigDecimal.class))
                        .volume(row.get("volume", BigDecimal.class))
                        .value(row.get("value", BigDecimal.class))
                        .numberOfTrades(row.get("number_of_trades", Integer.class))
                        .nav(row.get("nav") != null ? row.get("nav", BigDecimal.class).toPlainString() : null)
                        .navCurrency(row.get("nav_currency", String.class))
                        .build()
                )
                .all()
                .collectList();
    }

    @Override
    public Mono<AixMarketDepthDto> getMarketDepth(String symbol) {
        return aixClient.fetchMarketDepth(symbol);
    }

    @Override
    public Mono<List<ArbitrageItemDto>> getArbitrageOpportunities() {
        String sql = """
            SELECT
                k.code AS kase_code,
                COALESCE(k.org_short_name_ru, k.org_name_ru) AS company_name,
                k.price AS kase_price,
                COALESCE(NULLIF(t.currency, ''), NULLIF(k.currency_type, ''), 'KZT') AS kase_currency,
                t.nin AS kase_isin,
                a.sec_code AS aix_code,
                COALESCE(a.last_trade, a.reference_price) AS aix_price,
                a.currency AS aix_currency,
                a.isin AS aix_isin
            FROM security_instrument k
            LEFT JOIN ticker t ON k.id = t.security_instrument_id
            JOIN aix_security_instrument a ON (
                (t.nin IS NOT NULL AND t.nin != '' AND t.nin = a.isin)
                OR (t.nin2 IS NOT NULL AND t.nin2 != '' AND t.nin2 = a.isin)
                OR (k.code = 'KZAP' AND a.sec_code = 'KAP')
                OR (k.code = a.sec_code)
            )
            WHERE COALESCE(k.price, 0) > 0
              AND COALESCE(a.last_trade, a.reference_price, 0) > 0
              AND COALESCE(NULLIF(t.currency, ''), NULLIF(k.currency_type, ''), 'KZT') = a.currency
            ORDER BY k.code ASC
        """;

        return databaseClient.sql(sql)
                .map((row, meta) -> {
                    String kaseCode = row.get("kase_code", String.class);
                    String aixCode = row.get("aix_code", String.class);
                    String name = row.get("company_name", String.class);
                    String isin = row.get("aix_isin", String.class);
                    String cur = row.get("kase_currency", String.class);
                    BigDecimal kPrice = row.get("kase_price", BigDecimal.class);
                    BigDecimal aPrice = row.get("aix_price", BigDecimal.class);

                    BigDecimal spreadAbs = kPrice.subtract(aPrice);
                    BigDecimal minPrice = kPrice.min(aPrice);
                    BigDecimal spreadPct = BigDecimal.ZERO;
                    if (minPrice.compareTo(BigDecimal.ZERO) > 0) {
                        spreadPct = spreadAbs.abs().multiply(BigDecimal.valueOf(100)).divide(minPrice, 2, RoundingMode.HALF_UP);
                    }

                    String cheaper = kPrice.compareTo(aPrice) < 0 ? "KASE" : (kPrice.compareTo(aPrice) > 0 ? "AIX" : "РАВНО");
                    String rec;
                    if (cheaper.equals("AIX")) {
                        rec = String.format("Выгоднее купить на AIX (дешевле на %.2f%%, спред %s %s)", spreadPct.doubleValue(), spreadAbs.abs().setScale(2, RoundingMode.HALF_UP), cur);
                    } else if (cheaper.equals("KASE")) {
                        rec = String.format("Выгоднее купить на KASE (дешевле на %.2f%%, спред %s %s)", spreadPct.doubleValue(), spreadAbs.abs().setScale(2, RoundingMode.HALF_UP), cur);
                    } else {
                        rec = "Цены на обеих биржах совпадают";
                    }

                    return ArbitrageItemDto.builder()
                            .isin(isin)
                            .companyName(name != null ? name : kaseCode)
                            .kaseCode(kaseCode)
                            .kasePrice(kPrice)
                            .aixCode(aixCode)
                            .aixPrice(aPrice)
                            .currency(cur)
                            .spreadAbs(spreadAbs)
                            .spreadPercent(spreadPct)
                            .cheaperExchange(cheaper)
                            .recommendation(rec)
                            .build();
                })
                .all()
                .collectList();
    }

    @Override
    public Mono<ArbitrageItemDto> getArbitrageByTicker(String tickerOrIsin) {
        if (tickerOrIsin == null || tickerOrIsin.isBlank()) {
            return Mono.empty();
        }
        String target = tickerOrIsin.trim().toUpperCase();
        return getArbitrageOpportunities()
                .map(list -> list.stream()
                        .filter(item -> target.equalsIgnoreCase(item.getKaseCode())
                                || target.equalsIgnoreCase(item.getAixCode())
                                || target.equalsIgnoreCase(item.getIsin())
                                || (target.equals("KZAP") && "KAP".equalsIgnoreCase(item.getAixCode()))
                                || (target.equals("KAP") && "KZAP".equalsIgnoreCase(item.getKaseCode()))
                        )
                        .findFirst()
                        .orElse(null)
                );
    }
}
