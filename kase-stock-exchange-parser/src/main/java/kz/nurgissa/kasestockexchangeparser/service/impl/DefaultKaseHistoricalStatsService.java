package kz.nurgissa.kasestockexchangeparser.service.impl;

import kz.nurgissa.kasestockexchangeparser.model.entities.SecurityMarketTurnoverEntity;
import kz.nurgissa.kasestockexchangeparser.repositories.SecurityMarketTurnoverRepository;
import kz.nurgissa.kasestockexchangeparser.service.KaseHistoricalStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultKaseHistoricalStatsService implements KaseHistoricalStatsService {

    private final SecurityMarketTurnoverRepository turnoverRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        importStatsIfEmpty()
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        count -> {
                            if (count > 0) {
                                log.info("Successfully imported {} KASE historical stats entries on startup", count);
                            }
                        },
                        error -> log.error("Failed to auto-import KASE historical stats on startup: {}", error.getMessage())
                );
    }

    @Override
    public Mono<Integer> importStatsIfEmpty() {
        return turnoverRepository.count()
                .flatMap(count -> {
                    if (count > 0) {
                        log.debug("KASE market turnover stats table already contains {} records, skipping import", count);
                        return Mono.just(0);
                    }
                    return reloadStats();
                });
    }

    @Override
    public Mono<Integer> reloadStats() {
        return Mono.fromCallable(() -> {
            List<SecurityMarketTurnoverEntity> records = new ArrayList<>();

            // 1. Cumulative 2022-2026 file
            records.addAll(parseExcelFile(
                    "data/shares-stats-2022-2026.xlsx",
                    "CUMULATIVE_2022_2026",
                    LocalDate.of(2022, 1, 1),
                    LocalDate.of(2026, 8, 31)
            ));

            // 2. August 2026 monthly file
            records.addAll(parseExcelFile(
                    "data/shares-stats-2026-08.xlsx",
                    "MONTHLY_2026_08",
                    LocalDate.of(2026, 8, 1),
                    LocalDate.of(2026, 8, 31)
            ));

            return records;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(records -> {
            if (records.isEmpty()) {
                return Mono.just(0);
            }
            return Flux.fromIterable(records)
                    .flatMap(item -> turnoverRepository.findByCodeAndPeriodCode(item.getCode(), item.getPeriodCode())
                            .flatMap(existing -> {
                                item.setId(existing.getId());
                                item.setCreatedAt(existing.getCreatedAt());
                                return turnoverRepository.save(item);
                            })
                            .switchIfEmpty(turnoverRepository.save(item))
                    )
                    .count()
                    .map(Long::intValue);
        });
    }

    private List<SecurityMarketTurnoverEntity> parseExcelFile(
            String resourcePath,
            String periodCode,
            LocalDate startDate,
            LocalDate endDate
    ) {
        List<SecurityMarketTurnoverEntity> list = new ArrayList<>();
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            log.warn("Resource not found for historical stats: {}", resourcePath);
            return list;
        }

        try (InputStream is = resource.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            int lastRowNum = sheet.getLastRowNum();

            // Row 0: Title, Row 1: Headers, Data starts at Row 2 (0-indexed)
            for (int r = 2; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String code = getCellString(row.getCell(0));
                if (code == null || code.isBlank() || code.equalsIgnoreCase("Итого") || code.equalsIgnoreCase("Всего")) {
                    continue;
                }

                String companyName = getCellString(row.getCell(1));
                String sector = getCellString(row.getCell(2));
                Long dealsCount = getCellLong(row.getCell(3));

                // Volume in column E is in MILLION KZT in the official KASE sheet
                BigDecimal volMln = getCellBigDecimal(row.getCell(4));
                BigDecimal volumeKzt = volMln != null 
                        ? volMln.multiply(BigDecimal.valueOf(1_000_000L)).setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

                BigDecimal freeFloatPct = getCellBigDecimal(row.getCell(5));
                String ipoValue = getCellString(row.getCell(6));
                boolean isIpo = ipoValue != null && (ipoValue.contains("+") || ipoValue.equalsIgnoreCase("Да") || ipoValue.equalsIgnoreCase("Yes"));

                BigDecimal avgDealSize = BigDecimal.ZERO;
                if (dealsCount != null && dealsCount > 0 && volumeKzt.compareTo(BigDecimal.ZERO) > 0) {
                    avgDealSize = volumeKzt.divide(BigDecimal.valueOf(dealsCount), 2, RoundingMode.HALF_UP);
                }

                String participantType = "BALANCED";
                if (dealsCount != null && dealsCount <= 10 && avgDealSize.compareTo(BigDecimal.valueOf(1_000_000_000L)) >= 0) {
                    participantType = "BLOCK_DEALS"; // E.g. ALMS: 42.8B across 6 deals
                } else if (avgDealSize.compareTo(BigDecimal.valueOf(5_000_000L)) >= 0) {
                    participantType = "INSTITUTIONAL_HEAVY"; // E.g. KZTK: 2.75M average ticket
                } else if (avgDealSize.compareTo(BigDecimal.valueOf(350_000L)) <= 0) {
                    participantType = "RETAIL_DOMINATED"; // E.g. HSBK: 76k average ticket, AIRA: 235k
                }

                SecurityMarketTurnoverEntity entity = SecurityMarketTurnoverEntity.builder()
                        .code(code.trim().toUpperCase())
                        .companyName(companyName != null ? companyName.trim() : "")
                        .sector(sector != null ? sector.trim() : "акции")
                        .periodCode(periodCode)
                        .periodStart(startDate)
                        .periodEnd(endDate)
                        .dealsCount(dealsCount != null ? dealsCount : 0L)
                        .volumeKzt(volumeKzt)
                        .freeFloatPct(freeFloatPct)
                        .isIpoSpo(isIpo)
                        .avgDealSizeKzt(avgDealSize)
                        .participantType(participantType)
                        .createdAt(LocalDateTime.now())
                        .build();

                list.add(entity);
            }

            log.info("Parsed {} turnover records from {}", list.size(), resourcePath);
        } catch (Exception e) {
            log.error("Error parsing historical stats from {}: {}", resourcePath, e.getMessage(), e);
        }

        return list;
    }

    private String getCellString(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue();
        } else if (cell.getCellType() == CellType.NUMERIC) {
            return String.valueOf((long) cell.getNumericCellValue());
        }
        return null;
    }

    private Long getCellLong(Cell cell) {
        if (cell == null) return 0L;
        if (cell.getCellType() == CellType.NUMERIC) {
            return (long) cell.getNumericCellValue();
        } else if (cell.getCellType() == CellType.STRING) {
            try {
                String str = cell.getStringCellValue().replaceAll("\\s+", "").replace(",", ".");
                return Long.parseLong(str);
            } catch (Exception ignored) {}
        }
        return 0L;
    }

    private BigDecimal getCellBigDecimal(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).setScale(2, RoundingMode.HALF_UP);
        } else if (cell.getCellType() == CellType.STRING) {
            try {
                String str = cell.getStringCellValue().replaceAll("\\s+", "").replace(",", ".");
                return new BigDecimal(str).setScale(2, RoundingMode.HALF_UP);
            } catch (Exception ignored) {}
        }
        return null;
    }

    @Override
    public Flux<SecurityMarketTurnoverEntity> getCumulativeStats() {
        return turnoverRepository.findByPeriodCodeOrderByVolumeKztDesc("CUMULATIVE_2022_2026");
    }

    @Override
    public Flux<SecurityMarketTurnoverEntity> getMonthlyStats() {
        return turnoverRepository.findByPeriodCodeOrderByVolumeKztDesc("MONTHLY_2026_08");
    }

    @Override
    public Flux<SecurityMarketTurnoverEntity> getStatsByCode(String code) {
        if (code == null) return Flux.empty();
        return turnoverRepository.findByCode(code.trim().toUpperCase());
    }
}
