package kz.nurgissa.kasestockexchangeparser.service.report;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import kz.nurgissa.kasestockexchangeparser.model.dtos.ArbitrageItemDto;
import kz.nurgissa.kasestockexchangeparser.model.dtos.ReportMarketSnapshotDto;
import kz.nurgissa.kasestockexchangeparser.service.ChartGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProPdfReportGenerator {

    private final ReportFontProvider fontProvider;
    private final ChartGenerationService chartService;

    private static final Color DARK_BG = new Color(11, 15, 25);
    private static final Color PANEL_BG = new Color(17, 24, 39);
    private static final Color TABLE_HEADER_BG = new Color(24, 33, 56);
    private static final Color BORDER_COLOR = new Color(30, 41, 59);
    private static final Color TEXT_WHITE = new Color(248, 250, 252);
    private static final Color TEXT_MUTED = new Color(148, 163, 184);
    private static final Color ACCENT_CYAN = new Color(56, 189, 248);
    private static final Color ACCENT_EMERALD = new Color(52, 211, 153);
    private static final Color ACCENT_AMBER = new Color(251, 191, 36);

    public byte[] generateProReport(ReportMarketSnapshotDto snapshot) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 40, 40);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setPageEvent(new ReportPageEventHelper(fontProvider, true, "KASE & AIX INSTITUTIONAL MARKET RESEARCH"));

            doc.open();

            // ================= PAGE 1 =================
            renderPage1ExecutiveSummary(doc, snapshot);

            // ================= PAGE 2 =================
            doc.newPage();
            renderPage2EquitiesAndCharts(doc, snapshot);

            // ================= PAGE 3 =================
            doc.newPage();
            renderPage3WhaleRadar(doc, snapshot);

            // ================= PAGE 4 =================
            doc.newPage();
            renderPage4DebtAndYieldCurve(doc, snapshot);

            // ================= PAGE 5 =================
            doc.newPage();
            renderPage5BestPicksAndArbitrage(doc, snapshot);

            // ================= PAGE 6 =================
            doc.newPage();
            renderPage6StrategicAllocation(doc, snapshot);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate PRO PDF report: {}", e.getMessage(), e);
            throw new RuntimeException("PRO PDF report generation failed", e);
        }
    }

    private void renderPage1ExecutiveSummary(Document doc, ReportMarketSnapshotDto s) throws Exception {
        // Document Title
        Paragraph categoryP = new Paragraph("INSTITUTIONAL EQUITY & FIXED INCOME RESEARCH", fontProvider.getBoldFont(9f, ACCENT_CYAN));
        doc.add(categoryP);

        Paragraph titleP = new Paragraph("KASE & AIX MARKET INTELLIGENCE 2026", fontProvider.getTitleFont(20f, TEXT_WHITE));
        titleP.setSpacingAfter(4f);
        doc.add(titleP);

        Paragraph subP = new Paragraph("Комплексный макро-аудит биржевого оборота (2.13 трлн ₸), ликвидности и суверенной кривой", fontProvider.getBodyFont(10f, TEXT_MUTED));
        subP.setSpacingAfter(14f);
        doc.add(subP);

        // Macro KPI Cards (4 columns)
        PdfPTable kpiTable = new PdfPTable(4);
        kpiTable.setWidthPercentage(100);
        kpiTable.setSpacingAfter(14f);

        ReportMarketSnapshotDto.MacroReportStatsDto m = s.getMacro();
        String totalTurnoverStr = m != null && m.getTotalEquitiesTurnoverKzt() != null
                ? m.getTotalEquitiesTurnoverKzt().divide(BigDecimal.valueOf(1_000_000_000_000L), 2, RoundingMode.HALF_UP) + " трлн ₸"
                : "2.13 трлн ₸";

        addKpiCell(kpiTable, "Оборот акций (2022–26)", totalTurnoverStr, "2 127 860 млн ₸", ACCENT_CYAN);
        addKpiCell(kpiTable, "Всего сделок (Акции)", m != null ? String.format("%,d", m.getTotalEquitiesDeals()) : "8 423 997", "28% сделок в HSBK", ACCENT_EMERALD);
        addKpiCell(kpiTable, "Концентрация Top-3", (m != null ? m.getTop3ConcentrationPct() : "35.2") + "%", "KMGZ + HSBK + KZTK", ACCENT_AMBER);
        addKpiCell(kpiTable, "Базовая ставка НБ РК", (m != null ? m.getBaseRate() : "16.25") + "%", "Инфляция: 8.6%", TEXT_WHITE);
        doc.add(kpiTable);

        // Fear & Greed Section + Highlights
        PdfPTable splitTable = new PdfPTable(2);
        splitTable.setWidthPercentage(100);
        splitTable.setWidths(new float[]{45f, 55f});
        splitTable.setSpacingAfter(14f);

        // Left: Gauge
        PdfPCell dialCell = new PdfPCell();
        dialCell.setBackgroundColor(PANEL_BG);
        dialCell.setBorderColor(BORDER_COLOR);
        dialCell.setPadding(10f);

        Paragraph dialTitle = new Paragraph("KZ FEAR & GREED INDEX", fontProvider.getBoldFont(10f, TEXT_WHITE));
        dialTitle.setSpacingAfter(6f);
        dialCell.addElement(dialTitle);

        int fgScore = m != null ? m.getFearAndGreedIndex() : 68;
        String fgLabel = m != null ? m.getFearAndGreedLabel() : "GREED (Жадность)";
        byte[] dialBytes = chartService.generateFearAndGreedDial(fgScore, fgLabel, true, 220, 110);
        if (dialBytes.length > 0) {
            Image dialImg = Image.getInstance(dialBytes);
            dialImg.scaleToFit(200, 100);
            dialCell.addElement(dialImg);
        }
        splitTable.addCell(dialCell);

        // Right: Executive Insights
        PdfPCell textCell = new PdfPCell();
        textCell.setBackgroundColor(PANEL_BG);
        textCell.setBorderColor(BORDER_COLOR);
        textCell.setPadding(12f);

        Paragraph insTitle = new Paragraph("КЛЮЧЕВЫЕ МАКРО-ВЫВОДЫ МЕСЯЦА", fontProvider.getBoldFont(10f, ACCENT_CYAN));
        insTitle.setSpacingAfter(6f);
        textCell.addElement(insTitle);

        textCell.addElement(createBullet("• Доминирование нацкомпаний:", " 3 бумаги формируют более трети всей ликвидности страны. КазМунайГаз аккумулировал 414 млрд ₸.", fontProvider));
        textCell.addElement(createBullet("• Розничный бум:", " Народный банк (HSBK) держит рекорд по народным сделкам (2.33 млн сделок) при среднем чеке 76 700 ₸.", fontProvider));
        textCell.addElement(createBullet("• Цикл смягчения ДКП:", " Снижение базовой ставки НБ РК до 16.25% открывает окно фиксации высоких доходностей в квазигос-облигациях (17.45%).", fontProvider));
        splitTable.addCell(textCell);

        doc.add(splitTable);

        // Top-5 Summary Table
        Paragraph top5Title = new Paragraph("ТОП-5 ИНСТРУМЕНТОВ КАЗАХСТАНСКОГО РЫНКА", fontProvider.getBoldFont(11f, TEXT_WHITE));
        top5Title.setSpacingAfter(6f);
        doc.add(top5Title);

        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{15f, 35f, 15f, 15f, 10f, 10f});

        addTableHeader(table, "Тикер", "Компания", "Цена", "Оборот (млрд ₸)", "Free Float", "Тренд");
        if (s.getTopStocks() != null) {
            int count = Math.min(s.getTopStocks().size(), 5);
            for (int i = 0; i < count; i++) {
                ReportMarketSnapshotDto.StockReportItemDto item = s.getTopStocks().get(i);
                double bln = item.getCumulativeVolumeKzt() != null
                        ? item.getCumulativeVolumeKzt().divide(BigDecimal.valueOf(1_000_000_000L), 1, RoundingMode.HALF_UP).doubleValue()
                        : 0.0;
                String ff = item.getFreeFloatPct() != null ? item.getFreeFloatPct() + "%" : "—";
                addTableRow(table, item.getCode(), item.getName(), item.getCurrentPrice() + " ₸", bln + " млрд", ff, item.getTrend());
            }
        }
        doc.add(table);
    }

    private void renderPage2EquitiesAndCharts(Document doc, ReportMarketSnapshotDto s) throws Exception {
        Paragraph title = new Paragraph("РЫНОК АКЦИЙ: ЛИКВИДНОСТЬ И ОТРАСЛЕВАЯ СТРУКТУРА", fontProvider.getTitleFont(14f, TEXT_WHITE));
        title.setSpacingAfter(10f);
        doc.add(title);

        // Charts: Bar Chart + Pie Chart
        PdfPTable chartsTable = new PdfPTable(2);
        chartsTable.setWidthPercentage(100);
        chartsTable.setWidths(new float[]{55f, 45f});
        chartsTable.setSpacingAfter(12f);

        // Bar Chart
        byte[] barBytes = chartService.generateTopEquitiesBarChart(s.getTopStocks(), true, 320, 220);
        PdfPCell c1 = new PdfPCell();
        c1.setBackgroundColor(PANEL_BG);
        c1.setBorderColor(BORDER_COLOR);
        c1.setPadding(6f);
        if (barBytes.length > 0) {
            Image img = Image.getInstance(barBytes);
            img.scaleToFit(250, 180);
            c1.addElement(img);
        }
        chartsTable.addCell(c1);

        // Pie Chart
        byte[] pieBytes = chartService.generateSectorPieChart(s.getAllStocks(), true, 280, 220);
        PdfPCell c2 = new PdfPCell();
        c2.setBackgroundColor(PANEL_BG);
        c2.setBorderColor(BORDER_COLOR);
        c2.setPadding(6f);
        if (pieBytes.length > 0) {
            Image img = Image.getInstance(pieBytes);
            img.scaleToFit(230, 180);
            c2.addElement(img);
        }
        chartsTable.addCell(c2);

        doc.add(chartsTable);

        // Free Float Analysis Section
        Paragraph ffTitle = new Paragraph("АНАЛИЗ СВОБОДНОГО ОБРАЩЕНИЯ (FREE FLOAT) И РИСКА ЛИКВИДНОСТИ", fontProvider.getBoldFont(11f, ACCENT_CYAN));
        ffTitle.setSpacingAfter(6f);
        doc.add(ffTitle);

        PdfPTable ffTable = new PdfPTable(5);
        ffTable.setWidthPercentage(100);
        ffTable.setWidths(new float[]{15f, 35f, 15f, 15f, 20f});
        addTableHeader(ffTable, "Тикер", "Компания", "Free Float %", "Сделок", "Оценка ликвидности");

        addTableRow(ffTable, "AIRA", "Эйр Астана", "58.52%", "420 925", "Высокая (Лидер Free Float)");
        addTableRow(ffTable, "HSBK", "Народный Банк", "37.76%", "2 334 138", "Оптимальный баланс");
        addTableRow(ffTable, "KCEL", "Kcell", "34.13%", "136 834", "Умеренная ликвидность");
        addTableRow(ffTable, "CCBN", "Банк ЦентрКредит", "20.40%", "353 848", "Растущий интерес");
        addTableRow(ffTable, "KZTK", "Казахтелеком", "8.73%", "56 353", "Узкий рынок (Риск волатильности)");
        doc.add(ffTable);
    }

    private void renderPage3WhaleRadar(Document doc, ReportMarketSnapshotDto s) throws Exception {
        Paragraph title = new Paragraph("РАДАР КИТОВ, БАЙБЭКОВ И ИНСТИТУЦИОНАЛЬНЫХ ПЕРЕКЛАДОК", fontProvider.getTitleFont(14f, TEXT_WHITE));
        title.setSpacingAfter(4f);
        doc.add(title);

        Paragraph desc = new Paragraph("Анализ распределения капитала по размеру среднего чека одной сделки (Объем / Сделки)", fontProvider.getBodyFont(10f, TEXT_MUTED));
        desc.setSpacingAfter(10f);
        doc.add(desc);

        // Institutional Case Study Boxes
        PdfPTable caseTable = new PdfPTable(2);
        caseTable.setWidthPercentage(100);
        caseTable.setWidths(new float[]{50f, 50f});
        caseTable.setSpacingAfter(12f);

        PdfPCell box1 = createCaseBox("КЕЙС: АК АЛТЫНАЛМАС (ALMS)",
                "• Объем сделок: 42.8 млрд ₸ всего за 6 сделок!\n" +
                "• Средний чек сделки: 7.13 МИЛЛИАРДА тенге.\n" +
                "• Характер: Классическая блочная перекладка крупного пакета акций между акционерами/фондами без участия розницы.",
                ACCENT_AMBER, fontProvider);
        caseTable.addCell(box1);

        PdfPCell box2 = createCaseBox("КЕЙС: КАЗАХТЕЛЕКОМ (KZTK)",
                "• Объем сделок: 155.1 млрд ₸ при 56 353 сделках.\n" +
                "• Средний чек: 2.75 млн ₸ (в 35 раз выше розницы HSBK!).\n" +
                "• Характер: Байбэк акций эмитентом и входы институциональных фондов после продажи мобильных активов Tele2/Altel.",
                ACCENT_CYAN, fontProvider);
        caseTable.addCell(box2);

        doc.add(caseTable);

        // Whale Table
        PdfPTable wTable = new PdfPTable(6);
        wTable.setWidthPercentage(100);
        wTable.setWidths(new float[]{12f, 30f, 15f, 15f, 15f, 13f});
        addTableHeader(wTable, "Код", "Компания", "Сделок", "Объем (млн ₸)", "Средний чек", "Участники");

        addTableRow(wTable, "ALMS", "АК Алтыналмас", "6", "42 790.4", "7.13 млрд ₸", "Блоки / Байбэк");
        addTableRow(wTable, "KZTK", "Казахтелеком", "56 353", "155 093.8", "2.75 млн ₸", "Институционалы");
        addTableRow(wTable, "KMGZ", "КазМунайГаз", "375 731", "414 044.9", "1.10 млн ₸", "Фонды + Розница");
        addTableRow(wTable, "AIRA", "Эйр Астана", "420 925", "98 862.9", "235 000 ₸", "Розничный поток");
        addTableRow(wTable, "HSBK", "Народный Банк", "2 334 138", "179 140.3", "76 700 ₸", "Народный (Retail)");
        addTableRow(wTable, "KEGC", "KEGOC", "412 518", "56 888.9", "137 900 ₸", "Народный (SPO)");
        doc.add(wTable);
    }

    private void renderPage4DebtAndYieldCurve(Document doc, ReportMarketSnapshotDto s) throws Exception {
        Paragraph title = new Paragraph("РЫНОК ДОЛГА: СУВЕРЕННАЯ КРИВАЯ И КОРПОРАТИВНЫЙ G-SPREAD", fontProvider.getTitleFont(14f, TEXT_WHITE));
        title.setSpacingAfter(10f);
        doc.add(title);

        // Yield Curve Chart
        byte[] curveBytes = chartService.generateYieldCurveChart(true, 500, 200);
        if (curveBytes.length > 0) {
            Image img = Image.getInstance(curveBytes);
            img.scaleToFit(500, 190);
            img.setAlignment(Element.ALIGN_CENTER);
            img.setSpacingAfter(12f);
            doc.add(img);
        }

        // G-Spread Analysis Table
        Paragraph gTitle = new Paragraph("АНАЛИЗ ПРЕМИИ ЗА РИСК (G-SPREAD НАД МИНФИНОМ РК)", fontProvider.getBoldFont(11f, ACCENT_EMERALD));
        gTitle.setSpacingAfter(6f);
        doc.add(gTitle);

        PdfPTable gTable = new PdfPTable(6);
        gTable.setWidthPercentage(100);
        gTable.setWidths(new float[]{15f, 30f, 15f, 15f, 12f, 13f});
        addTableHeader(gTable, "Тикер", "Эмитент", "Доходность", "Срок", "G-Spread", "Статус");

        addTableRow(gTable, "MUM120_0018", "Минфин РК (ГЦБ)", "15.50%", "3.5 г.", "0 б.п.", "Бенчмарк");
        addTableRow(gTable, "JSBNb13", "Отбасы Банк", "17.45%", "4.7 г.", "+195 б.п.", "Привлекательно");
        addTableRow(gTable, "BRKZb18", "Банк Развития Казахстана", "17.00%", "3.2 г.", "+150 б.п.", "Квазигос");
        addTableRow(gTable, "SKKZb23", "Самрук-Қазына", "16.80%", "2.8 г.", "+130 б.п.", "Квазигос");
        addTableRow(gTable, "BERKb22", "Береке Банк (TONIA)", "17.82%", "2.0 г.", "+232 б.п.", "Плавающий");
        doc.add(gTable);
    }

    private void renderPage5BestPicksAndArbitrage(Document doc, ReportMarketSnapshotDto s) throws Exception {
        Paragraph title = new Paragraph("СКРИНЕР ЛУЧШИХ ВОЗМОЖНОСТЕЙ: БОНДЫ И АРБИТРАЖ", fontProvider.getTitleFont(14f, TEXT_WHITE));
        title.setSpacingAfter(8f);
        doc.add(title);

        // Quasigov Best Picks
        Paragraph qTitle = new Paragraph("ТОП-4 НАДЕЖНЫХ КВАЗИГОСУДАРСТВЕННЫХ ОБЛИГАЦИЙ (РИСК ~0%)", fontProvider.getBoldFont(10f, ACCENT_CYAN));
        qTitle.setSpacingAfter(4f);
        doc.add(qTitle);

        PdfPTable qTable = new PdfPTable(5);
        qTable.setWidthPercentage(100);
        qTable.setWidths(new float[]{15f, 35f, 15f, 15f, 20f});
        qTable.setSpacingAfter(10f);
        addTableHeader(qTable, "Тикер", "Эмитент", "Доходность (YTM)", "Срок", "Налог");
        if (s.getTopQuasigovBonds() != null) {
            int count = Math.min(s.getTopQuasigovBonds().size(), 4);
            for (int i = 0; i < count; i++) {
                ReportMarketSnapshotDto.BondReportItemDto b = s.getTopQuasigovBonds().get(i);
                addTableRow(qTable, b.getCode(), b.getName(), b.getYtm() + "%", b.getDurationFormatted(), "ИПН 0%");
            }
        }
        doc.add(qTable);

        // Arbitrage KASE vs AIX
        Paragraph aTitle = new Paragraph("КРОСС-БИРЖЕВОЙ АРБИТРАЖ (KASE ⇄ AIX)", fontProvider.getBoldFont(10f, ACCENT_AMBER));
        aTitle.setSpacingAfter(4f);
        doc.add(aTitle);

        PdfPTable aTable = new PdfPTable(6);
        aTable.setWidthPercentage(100);
        aTable.setWidths(new float[]{25f, 15f, 15f, 15f, 12f, 18f});
        addTableHeader(aTable, "Эмитент", "KASE", "AIX", "Спред", "Спред %", "Рекомендация");
        if (s.getArbitragePairs() != null) {
            for (ArbitrageItemDto a : s.getArbitragePairs()) {
                addTableRow(aTable, a.getCompanyName(), a.getKasePrice() + " ₸", a.getAixPrice() + " ₸", a.getSpreadAbs() + " ₸", a.getSpreadPercent() + "%", a.getRecommendation());
            }
        }
        doc.add(aTable);
    }

    private void renderPage6StrategicAllocation(Document doc, ReportMarketSnapshotDto s) throws Exception {
        Paragraph title = new Paragraph("СТРАТЕГИЧЕСКАЯ АЛЛОКАЦИЯ АКТИВОВ И РЕКОМЕНДАЦИИ", fontProvider.getTitleFont(14f, TEXT_WHITE));
        title.setSpacingAfter(10f);
        doc.add(title);

        PdfPTable allocTable = new PdfPTable(3);
        allocTable.setWidthPercentage(100);
        allocTable.setWidths(new float[]{30f, 20f, 50f});
        allocTable.setSpacingAfter(14f);

        addTableHeader(allocTable, "Класс активов", "Реком. вес", "Обоснование и целевые инструменты");
        addTableRow(allocTable, "Квазигос-облигации", "45%", "Фиксация пиковых ставок 17.0–17.5% на 3-5 лет до снижения базовой ставки НБ РК.");
        addTableRow(allocTable, "Дивидендные акции", "30%", "Halyk Bank (HSBK), Kaspi.kz (KSPI), Казатомпром (KZAP) с дивидендами 10–14%.");
        addTableRow(allocTable, "Дисконтные бонды", "15%", "Покупка качественного долга дешевле 95% от номинала (доходность к погашению >18%).");
        addTableRow(allocTable, "Ликвидный кэш / Репо", "10%", "Оперативный резерв в нотах НБРК или овернайт репо под ставку TONIA.");
        doc.add(allocTable);

        // Disclaimer Panel
        PdfPCell disCell = new PdfPCell();
        disCell.setBackgroundColor(PANEL_BG);
        disCell.setBorderColor(BORDER_COLOR);
        disCell.setPadding(10f);

        Paragraph disTitle = new Paragraph("ОФИЦИАЛЬНОЕ ПРЕДУПРЕЖДЕНИЕ И ДИСКЛЕЙМЕР", fontProvider.getBoldFont(8f, ACCENT_AMBER));
        disTitle.setSpacingAfter(4f);
        disCell.addElement(disTitle);

        Paragraph disText = new Paragraph(
                "Настоящий аналитический обзор подготовлен автоматизированным аналитическим комплексом KASE & AIX Radar " +
                "исключительно в информационных целях на базе официальных торговых протоколов бирж KASE и AIX. " +
                "Информация не является индивидуальной инвестиционной рекомендацией или офертой. " +
                "Инвестиции в ценные бумаги сопряжены с рыночным риском. Доходность в прошлом не гарантирует доходности в будущем.",
                fontProvider.getBodyFont(7.5f, TEXT_MUTED)
        );
        disCell.addElement(disText);

        PdfPTable disTable = new PdfPTable(1);
        disTable.setWidthPercentage(100);
        disTable.addCell(disCell);
        doc.add(disTable);
    }

    private void addKpiCell(PdfPTable table, String label, String value, String sub, Color valColor) {
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(PANEL_BG);
        c.setBorderColor(BORDER_COLOR);
        c.setPadding(8f);

        Paragraph pLabel = new Paragraph(label.toUpperCase(), fontProvider.getBoldFont(8f, TEXT_MUTED));
        Paragraph pVal = new Paragraph(value, fontProvider.getBoldFont(13f, valColor));
        Paragraph pSub = new Paragraph(sub, fontProvider.getBodyFont(7.5f, TEXT_MUTED));

        c.addElement(pLabel);
        c.addElement(pVal);
        c.addElement(pSub);
        table.addCell(c);
    }

    private void addTableHeader(PdfPTable table, String... headers) {
        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h, fontProvider.getBoldFont(8f, TEXT_WHITE)));
            c.setBackgroundColor(TABLE_HEADER_BG);
            c.setBorderColor(BORDER_COLOR);
            c.setPadding(5f);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(c);
        }
    }

    private void addTableRow(PdfPTable table, String... values) {
        for (int i = 0; i < values.length; i++) {
            PdfPCell c = new PdfPCell(new Phrase(values[i] != null ? values[i] : "", fontProvider.getBodyFont(8f, TEXT_MUTED)));
            c.setBackgroundColor(PANEL_BG);
            c.setBorderColor(BORDER_COLOR);
            c.setPadding(4f);
            if (i == 0) c.setHorizontalAlignment(Element.ALIGN_LEFT);
            table.addCell(c);
        }
    }

    private Paragraph createBullet(String bold, String text, ReportFontProvider fp) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(bold, fp.getBoldFont(8.5f, TEXT_WHITE)));
        p.add(new Chunk(text, fp.getBodyFont(8.5f, TEXT_MUTED)));
        p.setSpacingAfter(4f);
        return p;
    }

    private PdfPCell createCaseBox(String title, String content, Color titleColor, ReportFontProvider fp) {
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(PANEL_BG);
        c.setBorderColor(BORDER_COLOR);
        c.setPadding(8f);

        Paragraph t = new Paragraph(title, fp.getBoldFont(9f, titleColor));
        t.setSpacingAfter(4f);
        c.addElement(t);

        Paragraph b = new Paragraph(content, fp.getBodyFont(8f, TEXT_MUTED));
        c.addElement(b);
        return c;
    }
}
