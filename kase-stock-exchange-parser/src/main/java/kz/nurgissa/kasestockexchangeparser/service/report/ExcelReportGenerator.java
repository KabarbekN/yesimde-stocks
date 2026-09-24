package kz.nurgissa.kasestockexchangeparser.service.report;

import kz.nurgissa.kasestockexchangeparser.model.dtos.ArbitrageItemDto;
import kz.nurgissa.kasestockexchangeparser.model.dtos.ReportMarketSnapshotDto;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
public class ExcelReportGenerator {

    public byte[] generateExcelReport(ReportMarketSnapshotDto snapshot) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            // Styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle subHeaderStyle = createSubHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            CellStyle percentStyle = createPercentStyle(workbook);
            CellStyle boldStyle = createBoldStyle(workbook);

            // 1. Sheet: Equities
            buildEquitiesSheet(workbook, snapshot.getAllStocks(), headerStyle, subHeaderStyle, dataStyle, currencyStyle, percentStyle, boldStyle);

            // 2. Sheet: Bonds
            buildBondsSheet(workbook, snapshot.getAllBonds(), headerStyle, subHeaderStyle, dataStyle, currencyStyle, percentStyle, boldStyle);

            // 3. Sheet: Arbitrage
            buildArbitrageSheet(workbook, snapshot.getArbitragePairs(), headerStyle, subHeaderStyle, dataStyle, currencyStyle, percentStyle);

            // 4. Sheet: Macro & Whales
            buildMacroSheet(workbook, snapshot, headerStyle, subHeaderStyle, dataStyle, currencyStyle, percentStyle, boldStyle);

            workbook.write(baos);
            workbook.dispose();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate Excel report: {}", e.getMessage(), e);
            throw new RuntimeException("Excel report generation failed", e);
        }
    }

    private void buildEquitiesSheet(
            Workbook wb,
            List<ReportMarketSnapshotDto.StockReportItemDto> stocks,
            CellStyle headerStyle,
            CellStyle subHeaderStyle,
            CellStyle dataStyle,
            CellStyle currencyStyle,
            CellStyle percentStyle,
            CellStyle boldStyle
    ) {
        Sheet sheet = wb.createSheet("Акции (KASE и AIX)");
        sheet.createFreezePane(0, 2);

        // Title row
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("РЕЕСТР АКЦИЙ KASE И AIX (ПОЛНЫЙ БИРЖЕВОЙ СПИСОК С ЛИКВИДНОСТЬЮ И ОБОРОТАМИ 2022–2026)");
        titleCell.setCellStyle(boldStyle);

        // Header Row
        String[] headers = {
                "Код (Тикер)", "Эмитент", "Сектор", "Площадка", "Цена", "Валюта",
                "Изм. за день", "Оборот 2022–2026 (млрд ₸)", "Сделок (2022–2026)", "Средний чек (₸)",
                "Free Float %", "Тип движения", "RSI(14)", "Тренд"
        };

        Row headerRow = sheet.createRow(1);
        for (int i = 0; i < headers.length; i++) {
            Cell c = headerRow.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }

        int rowIdx = 2;
        if (stocks != null) {
            for (ReportMarketSnapshotDto.StockReportItemDto s : stocks) {
                Row row = sheet.createRow(rowIdx++);

                Cell c0 = row.createCell(0);
                c0.setCellValue(s.getCode());
                c0.setCellStyle(boldStyle);

                Cell c1 = row.createCell(1);
                c1.setCellValue(s.getName() != null ? s.getName() : "");
                c1.setCellStyle(dataStyle);

                Cell c2 = row.createCell(2);
                c2.setCellValue(s.getSector() != null ? s.getSector() : "акции");
                c2.setCellStyle(dataStyle);

                Cell c3 = row.createCell(3);
                c3.setCellValue(s.getExchange() != null ? s.getExchange() : "KASE");
                c3.setCellStyle(dataStyle);

                Cell c4 = row.createCell(4);
                if (s.getCurrentPrice() != null) c4.setCellValue(s.getCurrentPrice().doubleValue());
                c4.setCellStyle(currencyStyle);

                Cell c5 = row.createCell(5);
                c5.setCellValue(s.getCurrency() != null ? s.getCurrency() : "KZT");
                c5.setCellStyle(dataStyle);

                Cell c6 = row.createCell(6);
                if (s.getDayChangePct() != null) c6.setCellValue(s.getDayChangePct().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP).doubleValue());
                c6.setCellStyle(percentStyle);

                Cell c7 = row.createCell(7);
                if (s.getCumulativeVolumeKzt() != null) {
                    double bln = s.getCumulativeVolumeKzt().divide(BigDecimal.valueOf(1_000_000_000L), 2, RoundingMode.HALF_UP).doubleValue();
                    c7.setCellValue(bln);
                }
                c7.setCellStyle(currencyStyle);

                Cell c8 = row.createCell(8);
                if (s.getCumulativeDeals() != null) c8.setCellValue(s.getCumulativeDeals());
                c8.setCellStyle(dataStyle);

                Cell c9 = row.createCell(9);
                if (s.getAvgDealSizeKzt() != null) c9.setCellValue(s.getAvgDealSizeKzt().doubleValue());
                c9.setCellStyle(currencyStyle);

                Cell c10 = row.createCell(10);
                if (s.getFreeFloatPct() != null) c10.setCellValue(s.getFreeFloatPct().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP).doubleValue());
                c10.setCellStyle(percentStyle);

                Cell c11 = row.createCell(11);
                c11.setCellValue(formatParticipantType(s.getParticipantType()));
                c11.setCellStyle(dataStyle);

                Cell c12 = row.createCell(12);
                if (s.getRsi14() != null) c12.setCellValue(s.getRsi14());
                c12.setCellStyle(dataStyle);

                Cell c13 = row.createCell(13);
                c13.setCellValue(s.getTrend() != null ? s.getTrend() : "Нейтральный");
                c13.setCellStyle(dataStyle);
            }
        }

        // Set column widths
        for (int i = 0; i < headers.length; i++) {
            sheet.setColumnWidth(i, i == 1 ? 9000 : (i == 7 || i == 9 ? 6500 : 4500));
        }
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(1, Math.max(rowIdx - 1, 1), 0, headers.length - 1));
    }

    private void buildBondsSheet(
            Workbook wb,
            List<ReportMarketSnapshotDto.BondReportItemDto> bonds,
            CellStyle headerStyle,
            CellStyle subHeaderStyle,
            CellStyle dataStyle,
            CellStyle currencyStyle,
            CellStyle percentStyle,
            CellStyle boldStyle
    ) {
        Sheet sheet = wb.createSheet("Облигации KASE");
        sheet.createFreezePane(0, 2);

        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("ПОЛНЫЙ СКРИНЕР ОБЛИГАЦИЙ KASE (ГЦБ, КВАЗИГОССЕКТОР, ДИСКОНТЫ, ЕВРОБОНДЫ)");
        titleCell.setCellStyle(boldStyle);

        String[] headers = {
                "Тикер", "Эмитент", "Категория", "Валюта", "Доходность YTM", "Купонная ставка",
                "Срок до погашения", "Дата погашения", "Номинал", "Цена биржи", "Скидка (Дисконт %)", "Налог"
        };

        Row headerRow = sheet.createRow(1);
        for (int i = 0; i < headers.length; i++) {
            Cell c = headerRow.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }

        int rowIdx = 2;
        if (bonds != null) {
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            for (ReportMarketSnapshotDto.BondReportItemDto b : bonds) {
                Row row = sheet.createRow(rowIdx++);

                Cell c0 = row.createCell(0);
                c0.setCellValue(b.getCode());
                c0.setCellStyle(boldStyle);

                Cell c1 = row.createCell(1);
                c1.setCellValue(b.getName() != null ? b.getName() : "");
                c1.setCellStyle(dataStyle);

                Cell c2 = row.createCell(2);
                c2.setCellValue(formatBondCategory(b.getCategory()));
                c2.setCellStyle(dataStyle);

                Cell c3 = row.createCell(3);
                c3.setCellValue(b.getCurrency() != null ? b.getCurrency() : "KZT");
                c3.setCellStyle(dataStyle);

                Cell c4 = row.createCell(4);
                if (b.getYtm() != null) c4.setCellValue(b.getYtm().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP).doubleValue());
                c4.setCellStyle(percentStyle);

                Cell c5 = row.createCell(5);
                if (b.getCoupon() != null) c5.setCellValue(b.getCoupon().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP).doubleValue());
                c5.setCellStyle(percentStyle);

                Cell c6 = row.createCell(6);
                c6.setCellValue(b.getDurationFormatted() != null ? b.getDurationFormatted() : "");
                c6.setCellStyle(dataStyle);

                Cell c7 = row.createCell(7);
                if (b.getFinishDate() != null) c7.setCellValue(b.getFinishDate().format(dtf));
                c7.setCellStyle(dataStyle);

                Cell c8 = row.createCell(8);
                if (b.getNominal() != null) c8.setCellValue(b.getNominal().doubleValue());
                c8.setCellStyle(currencyStyle);

                Cell c9 = row.createCell(9);
                if (b.getCurrentPrice() != null) c9.setCellValue(b.getCurrentPrice().doubleValue());
                c9.setCellStyle(currencyStyle);

                Cell c10 = row.createCell(10);
                if (b.getDiscountPct() != null) c10.setCellValue(b.getDiscountPct().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP).doubleValue());
                c10.setCellStyle(percentStyle);

                Cell c11 = row.createCell(11);
                c11.setCellValue(Boolean.TRUE.equals(b.getIsTaxExempt()) ? "ИПН 0%" : "Облагается");
                c11.setCellStyle(dataStyle);
            }
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.setColumnWidth(i, i == 1 ? 9500 : (i == 4 || i == 5 ? 4500 : 4000));
        }
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(1, Math.max(rowIdx - 1, 1), 0, headers.length - 1));
    }

    private void buildArbitrageSheet(
            Workbook wb,
            List<ArbitrageItemDto> pairs,
            CellStyle headerStyle,
            CellStyle subHeaderStyle,
            CellStyle dataStyle,
            CellStyle currencyStyle,
            CellStyle percentStyle
    ) {
        Sheet sheet = wb.createSheet("Арбитраж (KASE vs AIX)");
        sheet.createFreezePane(0, 2);

        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("АРБИТРАЖНЫЙ МОНИТОР ДВОЙНОГО ЛИСТИНГА (KASE ⇄ AIX)");
        titleCell.setCellStyle(headerStyle);

        String[] headers = {
                "Эмитент", "Тикер KASE", "Тикер AIX", "Цена KASE", "Цена AIX",
                "Валюта", "Разница (Спред)", "Спред %", "Рекомендация по покупке"
        };

        Row headerRow = sheet.createRow(1);
        for (int i = 0; i < headers.length; i++) {
            Cell c = headerRow.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }

        int rowIdx = 2;
        if (pairs != null) {
            for (ArbitrageItemDto a : pairs) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(a.getCompanyName());
                row.createCell(1).setCellValue(a.getKaseCode());
                row.createCell(2).setCellValue(a.getAixCode());

                Cell c3 = row.createCell(3);
                if (a.getKasePrice() != null) c3.setCellValue(a.getKasePrice().doubleValue());
                c3.setCellStyle(currencyStyle);

                Cell c4 = row.createCell(4);
                if (a.getAixPrice() != null) c4.setCellValue(a.getAixPrice().doubleValue());
                c4.setCellStyle(currencyStyle);

                row.createCell(5).setCellValue(a.getCurrency() != null ? a.getCurrency() : "KZT");

                Cell c6 = row.createCell(6);
                if (a.getSpreadAbs() != null) c6.setCellValue(a.getSpreadAbs().doubleValue());
                c6.setCellStyle(currencyStyle);

                Cell c7 = row.createCell(7);
                if (a.getSpreadPercent() != null) c7.setCellValue(a.getSpreadPercent().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP).doubleValue());
                c7.setCellStyle(percentStyle);

                row.createCell(8).setCellValue(a.getRecommendation() != null ? a.getRecommendation() : "");
            }
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.setColumnWidth(i, i == 0 || i == 8 ? 8500 : 4200);
        }
    }

    private void buildMacroSheet(
            Workbook wb,
            ReportMarketSnapshotDto snapshot,
            CellStyle headerStyle,
            CellStyle subHeaderStyle,
            CellStyle dataStyle,
            CellStyle currencyStyle,
            CellStyle percentStyle,
            CellStyle boldStyle
    ) {
        Sheet sheet = wb.createSheet("Макро и Обороты");

        int r = 0;
        Row r0 = sheet.createRow(r++);
        Cell c0 = r0.createCell(0);
        c0.setCellValue("МАКРОЭКОНОМИЧЕСКИЕ ПАРАМЕТРЫ И ИТОГИ ТОРГОВ KASE (2022–2026)");
        c0.setCellStyle(boldStyle);

        sheet.createRow(r++); // Empty spacer

        Row headerMacro = sheet.createRow(r++);
        headerMacro.createCell(0).setCellValue("Параметр рынка");
        headerMacro.getCell(0).setCellStyle(headerStyle);
        headerMacro.createCell(1).setCellValue("Значение");
        headerMacro.getCell(1).setCellStyle(headerStyle);
        headerMacro.createCell(2).setCellValue("Источник / Описание");
        headerMacro.getCell(2).setCellStyle(headerStyle);

        if (snapshot.getMacro() != null) {
            ReportMarketSnapshotDto.MacroReportStatsDto m = snapshot.getMacro();
            addMacroRow(sheet, r++, "Совокупный оборот рынка акций (2022–2026)", m.getTotalEquitiesTurnoverKzt() != null ? m.getTotalEquitiesTurnoverKzt().doubleValue() : 0.0, currencyStyle, "Биржа KASE (Официальные отчеты)");
            addMacroRow(sheet, r++, "Всего сделок в секции акций", m.getTotalEquitiesDeals() != null ? m.getTotalEquitiesDeals().doubleValue() : 0.0, currencyStyle, "Биржа KASE");
            addMacroRow(sheet, r++, "Концентрация ликвидности Top-3", m.getTop3ConcentrationPct() != null ? m.getTop3ConcentrationPct().doubleValue() / 100.0 : 0.0, percentStyle, "KMGZ + HSBK + KZTK");
            addMacroRow(sheet, r++, "Базовая ставка Национального Банка РК", m.getBaseRate() != null ? m.getBaseRate().doubleValue() / 100.0 : 0.0, percentStyle, "НБ РК (сентябрь 2026)");
            addMacroRow(sheet, r++, "Годовая инфляция", m.getInflationRate() != null ? m.getInflationRate().doubleValue() / 100.0 : 0.0, percentStyle, "БНС АСПиР РК");
        }

        sheet.createRow(r++); // Empty spacer

        if (snapshot.getBattle() != null) {
            ReportMarketSnapshotDto.AssetBattleComparisonDto b = snapshot.getBattle();
            Row battleTitle = sheet.createRow(r++);
            battleTitle.createCell(0).setCellValue("СРАВНЕНИЕ ДОХОДНОСТЕЙ НА КАПИТАЛ: " + (b.getCapitalAmount() != null ? b.getCapitalAmount() : "26 500 000") + " ₸");
            battleTitle.getCell(0).setCellStyle(boldStyle);

            Row b1 = sheet.createRow(r++);
            b1.createCell(0).setCellValue("1. Квазигос-облигации KASE (Отбасы/БРК/Самрук)");
            b1.createCell(1).setCellValue(b.getBondYield() != null ? b.getBondYield().doubleValue() / 100.0 : 0.0);
            b1.getCell(1).setCellStyle(percentStyle);
            b1.createCell(2).setCellValue("+" + (b.getBondAnnualIncome() != null ? b.getBondAnnualIncome() : "0") + " ₸/год чистыми (ИПН 0%, фиксация на 3-5 лет)");

            Row b2 = sheet.createRow(r++);
            b2.createCell(0).setCellValue("2. Банковский депозит (Kaspi/Halyk ГЭСВ)");
            b2.createCell(1).setCellValue(b.getDepositRate() != null ? b.getDepositRate().doubleValue() / 100.0 : 0.0);
            b2.getCell(1).setCellStyle(percentStyle);
            b2.createCell(2).setCellValue("+" + (b.getDepositAnnualIncome() != null ? b.getDepositAnnualIncome() : "0") + " ₸/год (Лимит КФГД 10-20 млн ₸)");

            Row b3 = sheet.createRow(r++);
            b3.createCell(0).setCellValue("3. 1-комн. квартира в Алматы (аренда Krisha.kz)");
            b3.createCell(1).setCellValue(b.getRealEstateNetYield() != null ? b.getRealEstateNetYield().doubleValue() / 100.0 : 0.0);
            b3.getCell(1).setCellStyle(percentStyle);
            b3.createCell(2).setCellValue("+" + (b.getRealEstateNetAnnualIncome() != null ? b.getRealEstateNetAnnualIncome() : "0") + " ₸/год (Чистыми за вычетом простоя и ремонта)");
        }

        sheet.setColumnWidth(0, 11000);
        sheet.setColumnWidth(1, 6000);
        sheet.setColumnWidth(2, 14000);
    }

    private void addMacroRow(Sheet sheet, int r, String param, Double value, CellStyle style, String desc) {
        Row row = sheet.createRow(r);
        row.createCell(0).setCellValue(param);
        Cell c1 = row.createCell(1);
        if (value != null) c1.setCellValue(value);
        c1.setCellStyle(style);
        row.createCell(2).setCellValue(desc);
    }

    private String formatParticipantType(String type) {
        if (type == null) return "Смешанный";
        return switch (type) {
            case "BLOCK_DEALS" -> "Байбэк / Блочные сделки";
            case "INSTITUTIONAL_HEAVY" -> "Институционалы / Фонды";
            case "RETAIL_DOMINATED" -> "Розничный фаворит";
            default -> "Сбалансированный";
        };
    }

    private String formatBondCategory(String cat) {
        if (cat == null) return "Корпоративная";
        return switch (cat) {
            case "SOVEREIGN" -> "ГЦБ Минфина РК";
            case "QUASIGOV" -> "Квазигоссектор";
            case "DISCOUNT" -> "Скидка (<95%)";
            case "FOREIGN_CURRENCY" -> "Валютная (USD/EUR)";
            default -> "Корпоративная";
        };
    }

    // Styles creation helpers
    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        f.setFontHeightInPoints((short) 10);
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        return s;
    }

    private CellStyle createSubHeaderStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 10);
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    private CellStyle createDataStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        s.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        return s;
    }

    private CellStyle createBoldStyle(Workbook wb) {
        CellStyle s = createDataStyle(wb);
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        return s;
    }

    private CellStyle createCurrencyStyle(Workbook wb) {
        CellStyle s = createDataStyle(wb);
        s.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        s.setAlignment(HorizontalAlignment.RIGHT);
        return s;
    }

    private CellStyle createPercentStyle(Workbook wb) {
        CellStyle s = createDataStyle(wb);
        s.setDataFormat(wb.createDataFormat().getFormat("0.00%"));
        s.setAlignment(HorizontalAlignment.RIGHT);
        return s;
    }
}
