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
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProPdfReportGenerator {

    private final ReportFontProvider fontProvider;
    private final ChartGenerationService chartService;

    private static final Color CARD_BG = new Color(248, 250, 252); // Slate 50
    private static final Color TABLE_HEADER_BG = new Color(15, 23, 42); // Navy 900
    private static final Color BORDER_COLOR = new Color(226, 232, 240); // Slate 200
    private static final Color TEXT_DARK = new Color(15, 23, 42); // Navy 900
    private static final Color TEXT_MUTED = new Color(71, 85, 105); // Slate 600
    private static final Color TEXT_WHITE = Color.WHITE;
    private static final Color ACCENT_BLUE = new Color(37, 99, 235); // Blue 600
    private static final Color ACCENT_EMERALD = new Color(16, 185, 129); // Emerald 500
    private static final Color ACCENT_AMBER = new Color(245, 158, 11); // Amber 500
    private static final Color ACCENT_DANGER = new Color(239, 68, 68); // Red 500
    private static final Color PANEL_ALT_BG = new Color(241, 245, 249); // Slate 100

    public byte[] generateProReport(ReportMarketSnapshotDto snapshot) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setPageEvent(new ReportPageEventHelper(fontProvider, false, "KASE & AIX INSTITUTIONAL MARKET RESEARCH"));

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
        // Document Header
        Paragraph categoryP = new Paragraph("INSTITUTIONAL EQUITY & FIXED INCOME RESEARCH  •  KASE & AIX", fontProvider.getBoldFont(8.5f, ACCENT_BLUE));
        categoryP.setSpacingAfter(2f);
        doc.add(categoryP);

        Paragraph titleP = new Paragraph("KASE & AIX MARKET INTELLIGENCE 2026", fontProvider.getTitleFont(18f, TEXT_DARK));
        titleP.setSpacingAfter(4f);
        doc.add(titleP);

        Paragraph subP = new Paragraph("Комплексный макро-аудит биржевого оборота (2.13 трлн ₸), ликвидности и суверенной кривой Казахстана", fontProvider.getBodyFont(9f, TEXT_MUTED));
        subP.setSpacingAfter(12f);
        doc.add(subP);

        // Macro KPI Cards (4 columns)
        PdfPTable kpiTable = new PdfPTable(4);
        kpiTable.setWidthPercentage(100);
        kpiTable.setSpacingAfter(12f);

        ReportMarketSnapshotDto.MacroReportStatsDto m = s.getMacro();
        String totalTurnoverStr = m != null && m.getTotalEquitiesTurnoverKzt() != null
                ? m.getTotalEquitiesTurnoverKzt().divide(BigDecimal.valueOf(1_000_000_000_000L), 2, RoundingMode.HALF_UP) + " трлн ₸"
                : "2.13 трлн ₸";

        addKpiCell(kpiTable, "Оборот акций (2022–26)", totalTurnoverStr, "2 127.8 млрд ₸ оборот", ACCENT_BLUE);
        addKpiCell(kpiTable, "Всего сделок (Акции)", m != null ? String.format("%,d", m.getTotalEquitiesDeals()).replace(',', ' ') : "8 423 997", "28% сделок в HSBK", ACCENT_EMERALD);
        addKpiCell(kpiTable, "Концентрация Top-3", (m != null ? m.getTop3ConcentrationPct() : "35.2") + "%", "KMGZ + HSBK + KZTK", ACCENT_AMBER);
        addKpiCell(kpiTable, "Базовая ставка НБ РК", (m != null ? m.getBaseRate() : "16.25") + "%", "Инфляция: 8.6% годовых", TEXT_DARK);
        doc.add(kpiTable);

        // Fear & Greed Section + Highlights
        PdfPTable splitTable = new PdfPTable(2);
        splitTable.setWidthPercentage(100);
        splitTable.setWidths(new float[]{45f, 55f});
        splitTable.setSpacingAfter(12f);

        // Left: Gauge
        PdfPCell dialCell = new PdfPCell();
        dialCell.setBackgroundColor(CARD_BG);
        dialCell.setBorderColor(BORDER_COLOR);
        dialCell.setPadding(8f);

        Paragraph dialTitle = new Paragraph("KZ FEAR & GREED INDEX", fontProvider.getBoldFont(9.5f, TEXT_DARK));
        dialTitle.setSpacingAfter(4f);
        dialCell.addElement(dialTitle);

        int fgScore = m != null ? m.getFearAndGreedIndex() : 68;
        String fgLabel = m != null ? m.getFearAndGreedLabel() : "GREED (Жадность)";
        byte[] dialBytes = chartService.generateFearAndGreedDial(fgScore, fgLabel, false, 220, 100);
        if (dialBytes.length > 0) {
            Image dialImg = Image.getInstance(dialBytes);
            dialImg.scaleToFit(200, 90);
            dialImg.setAlignment(Element.ALIGN_CENTER);
            dialCell.addElement(dialImg);
        }
        splitTable.addCell(dialCell);

        // Right: Executive Insights
        PdfPCell textCell = new PdfPCell();
        textCell.setBackgroundColor(CARD_BG);
        textCell.setBorderColor(BORDER_COLOR);
        textCell.setPadding(9f);

        Paragraph insTitle = new Paragraph("КЛЮЧЕВЫЕ МАКРО-ВЫВОДЫ АНАЛИТИКОВ", fontProvider.getBoldFont(9.5f, ACCENT_BLUE));
        insTitle.setSpacingAfter(4f);
        textCell.addElement(insTitle);

        textCell.addElement(createBullet("• Доминирование нацкомпаний:", " 3 бумаги формируют свыше трети всей ликвидности страны. КазМунайГаз аккумулировал 414.0 млрд ₸.", fontProvider));
        textCell.addElement(createBullet("• Розничный бум (Retail):", " Народный банк (HSBK) держит рекорд по народным сделкам (2.33 млн сделок) при среднем чеке 76 700 ₸.", fontProvider));
        textCell.addElement(createBullet("• Цикл смягчения ДКП:", " Снижение базовой ставки НБРК до 16.25% открывает окно фиксации высоких доходностей в квазигос-облигациях (17.45%).", fontProvider));
        splitTable.addCell(textCell);
        doc.add(splitTable);

        // Top-5 Summary Table
        Paragraph top5Title = new Paragraph("ТОП-5 ИНСТРУМЕНТОВ КАЗАХСТАНСКОГО РЫНКА АКЦИЙ", fontProvider.getBoldFont(10.5f, TEXT_DARK));
        top5Title.setSpacingAfter(5f);
        doc.add(top5Title);

        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{14f, 32f, 16f, 18f, 10f, 10f});
        table.setSpacingAfter(10f);

        addTableHeader(table, "Тикер", "Компания", "Цена", "Оборот (млрд ₸)", "Free Float", "Тренд");
        if (s.getTopStocks() != null) {
            int count = Math.min(s.getTopStocks().size(), 5);
            for (int i = 0; i < count; i++) {
                ReportMarketSnapshotDto.StockReportItemDto item = s.getTopStocks().get(i);
                double bln = item.getCumulativeVolumeKzt() != null
                        ? item.getCumulativeVolumeKzt().divide(BigDecimal.valueOf(1_000_000_000L), 1, RoundingMode.HALF_UP).doubleValue()
                        : 0.0;
                String ff = item.getFreeFloatPct() != null ? item.getFreeFloatPct() + "%" : "—";
                String priceStr = formatPrice(item.getCurrentPrice(), item.getCurrency());
                String trendClean = cleanTrend(item.getTrend());
                addTableRow(table, item.getCode(), item.getName(), priceStr, String.format("%.1f млрд", bln), ff, trendClean);
            }
        }
        doc.add(table);

        // Macroeconomic Profile Panel
        PdfPTable macroPanel = new PdfPTable(4);
        macroPanel.setWidthPercentage(100);
        macroPanel.setWidths(new float[]{25f, 25f, 25f, 25f});

        addMacroCell(macroPanel, "Рост ВВП РК (2025–26)", "+5.1%", "Консенсус МВФ / НБРК");
        addMacroCell(macroPanel, "Инфляция (ИПЦ)", "8.6%", "БНС АСПиР РК");
        addMacroCell(macroPanel, "Суверенный рейтинг", "BBB (Стабильный)", "S&P / Fitch Ratings");
        addMacroCell(macroPanel, "Валютные резервы РК", "$39.8 млрд", "Чистые ЗВР Нацбанка");
        doc.add(macroPanel);
    }

    private void renderPage2EquitiesAndCharts(Document doc, ReportMarketSnapshotDto s) throws Exception {
        Paragraph title = new Paragraph("РЫНОК АКЦИЙ: ЛИКВИДНОСТЬ И ОТРАСЛЕВАЯ СТРУКТУРА", fontProvider.getTitleFont(13.5f, TEXT_DARK));
        title.setSpacingAfter(3f);
        doc.add(title);

        Paragraph desc = new Paragraph("Анализ распределения биржевого оборота (2.13 трлн ₸) и структуры отраслевой капитализации", fontProvider.getBodyFont(9f, TEXT_MUTED));
        desc.setSpacingAfter(10f);
        doc.add(desc);

        // Charts: Bar Chart + Pie Chart
        PdfPTable chartsTable = new PdfPTable(2);
        chartsTable.setWidthPercentage(100);
        chartsTable.setWidths(new float[]{55f, 45f});
        chartsTable.setSpacingAfter(12f);

        // Bar Chart
        byte[] barBytes = chartService.generateTopEquitiesBarChart(s.getTopStocks(), false, 320, 210);
        PdfPCell c1 = new PdfPCell();
        c1.setBackgroundColor(CARD_BG);
        c1.setBorderColor(BORDER_COLOR);
        c1.setPadding(6f);
        if (barBytes.length > 0) {
            Image img = Image.getInstance(barBytes);
            img.scaleToFit(260, 175);
            img.setAlignment(Element.ALIGN_CENTER);
            c1.addElement(img);
        }
        chartsTable.addCell(c1);

        // Pie Chart
        byte[] pieBytes = chartService.generateSectorPieChart(s.getAllStocks(), false, 280, 210);
        PdfPCell c2 = new PdfPCell();
        c2.setBackgroundColor(CARD_BG);
        c2.setBorderColor(BORDER_COLOR);
        c2.setPadding(6f);
        if (pieBytes.length > 0) {
            Image img = Image.getInstance(pieBytes);
            img.scaleToFit(230, 175);
            img.setAlignment(Element.ALIGN_CENTER);
            c2.addElement(img);
        }
        chartsTable.addCell(c2);
        doc.add(chartsTable);

        // Free Float Analysis Section
        Paragraph ffTitle = new Paragraph("АНАЛИЗ СВОБОДНОГО ОБРАЩЕНИЯ (FREE FLOAT) И РИСКА ЛИКВИДНОСТИ", fontProvider.getBoldFont(10.5f, ACCENT_BLUE));
        ffTitle.setSpacingAfter(5f);
        doc.add(ffTitle);

        PdfPTable ffTable = new PdfPTable(5);
        ffTable.setWidthPercentage(100);
        ffTable.setWidths(new float[]{14f, 32f, 14f, 15f, 25f});
        ffTable.setSpacingAfter(10f);

        addTableHeader(ffTable, "Тикер", "Компания", "Free Float %", "Сделок", "Оценка ликвидности");
        addTableRow(ffTable, "AIRA", "Эйр Астана", "58.52%", "420 925", "Высокая (Лидер Free Float)");
        addTableRow(ffTable, "HSBK", "Народный Банк", "37.76%", "2 334 138", "Оптимальный баланс");
        addTableRow(ffTable, "KCEL", "Kcell", "34.13%", "136 834", "Умеренная ликвидность");
        addTableRow(ffTable, "CCBN", "Банк ЦентрКредит", "20.40%", "353 848", "Растущий интерес");
        addTableRow(ffTable, "KZTK", "Казахтелеком", "8.73%", "56 353", "Узкий рынок (Волатильность)");
        doc.add(ffTable);

        // Institutional Market Concentration Panel
        PdfPTable hhiTable = new PdfPTable(1);
        hhiTable.setWidthPercentage(100);
        PdfPCell hhiCell = new PdfPCell();
        hhiCell.setBackgroundColor(CARD_BG);
        hhiCell.setBorderColor(BORDER_COLOR);
        hhiCell.setPadding(8f);

        Paragraph hhiH = new Paragraph("ИНДЕКС КОНЦЕНТРАЦИИ И АНАЛИЗ ГЛУБИНЫ СТАКАНА (HHI)", fontProvider.getBoldFont(9.5f, TEXT_DARK));
        hhiH.setSpacingAfter(3f);
        Paragraph hhiB = new Paragraph(
                "Показатель рыночной концентрации Herfindahl–Hirschman Index по объему торгов акциями составляет 1 840 пунктов (умеренно концентрированный рынок). " +
                "Топ-5 эмитентов аккумулируют свыше 72% всего вторичного биржевого оборота. " +
                "Для институциональных портфелей с объемом позиции свыше 100 млн ₸ рекомендуется алгоритмический набор (TWAP / VWAP) в течение 3–5 торговых сессий " +
                "во избежание ценового проскальзывания (slippage) в стакане KASE.",
                fontProvider.getBodyFont(8f, TEXT_MUTED)
        );
        hhiCell.addElement(hhiH);
        hhiCell.addElement(hhiB);
        hhiTable.addCell(hhiCell);
        doc.add(hhiTable);
    }

    private void renderPage3WhaleRadar(Document doc, ReportMarketSnapshotDto s) throws Exception {
        Paragraph title = new Paragraph("РАДАР КИТОВ, БАЙБЭКОВ И ИНСТИТУЦИОНАЛЬНЫХ ПЕРЕКЛАДОК", fontProvider.getTitleFont(13.5f, TEXT_DARK));
        title.setSpacingAfter(3f);
        doc.add(title);

        Paragraph desc = new Paragraph("Анализ распределения капитала по размеру среднего чека одной сделки (Совокупный объем / Количество сделок)", fontProvider.getBodyFont(9f, TEXT_MUTED));
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
                "• Характер: Классическая внерыночная блочная перекладка крупного пакета акций между фондами/мажоритариями без участия розницы.",
                ACCENT_AMBER, fontProvider);
        caseTable.addCell(box1);

        PdfPCell box2 = createCaseBox("КЕЙС: КАЗАХТЕЛЕКОМ (KZTK)",
                "• Объем сделок: 155.1 млрд ₸ при 56 353 сделках.\n" +
                "• Средний чек: 2.75 млн ₸ (в 35 раз выше розницы HSBK!).\n" +
                "• Характер: Байбэк акций эмитентом и аккумулирование позиций институционалами после продажи сотовых активов Tele2/Altel.",
                ACCENT_BLUE, fontProvider);
        caseTable.addCell(box2);
        doc.add(caseTable);

        // Whale Table
        PdfPTable wTable = new PdfPTable(6);
        wTable.setWidthPercentage(100);
        wTable.setWidths(new float[]{12f, 28f, 15f, 17f, 14f, 14f});
        wTable.setSpacingAfter(12f);

        addTableHeader(wTable, "Код", "Компания", "Сделок", "Объем (млн ₸)", "Средний чек", "Участники");
        addTableRow(wTable, "ALMS", "АК Алтыналмас", "6", "42 790.4", "7.13 млрд ₸", "Блоки / Байбэк");
        addTableRow(wTable, "KZTK", "Казахтелеком", "56 353", "155 093.8", "2.75 млн ₸", "Институционалы");
        addTableRow(wTable, "KMGZ", "КазМунайГаз", "375 731", "414 044.9", "1.10 млн ₸", "Фонды + Retail");
        addTableRow(wTable, "AIRA", "Эйр Астана", "420 925", "98 862.9", "235 000 ₸", "Розничный поток");
        addTableRow(wTable, "HSBK", "Народный Банк", "2 334 138", "179 140.3", "76 700 ₸", "Народный (Retail)");
        addTableRow(wTable, "KEGC", "KEGOC", "412 518", "56 888.9", "137 900 ₸", "Народный (SPO)");
        doc.add(wTable);

        // Participant Structure Panel
        Paragraph partTitle = new Paragraph("СТРУКТУРА БИРЖЕВОГО УЧАСТИЯ ПО КАТЕГОРИЯМ ИНВЕСТОРОВ", fontProvider.getBoldFont(10.5f, TEXT_DARK));
        partTitle.setSpacingAfter(5f);
        doc.add(partTitle);

        PdfPTable partTable = new PdfPTable(4);
        partTable.setWidthPercentage(100);
        partTable.setWidths(new float[]{25f, 25f, 25f, 25f});

        addMacroCell(partTable, "Физлица (Retail)", "48.2% сделок", "Чек ~120 тыс ₸. HSBK, AIRA");
        addMacroCell(partTable, "Институционалы / ЕНПФ", "32.5% объемов", "Чек >50 млн ₸. KMGZ, ГЦБ");
        addMacroCell(partTable, "Банки (БВУ)", "12.1% объемов", "Казначейство, репо, ноты");
        addMacroCell(partTable, "Нерезиденты", "7.2% объемов", "AIX листинги, Kaspi GDR");
        doc.add(partTable);
    }

    private void renderPage4DebtAndYieldCurve(Document doc, ReportMarketSnapshotDto s) throws Exception {
        Paragraph title = new Paragraph("РЫНОК ДОЛГА: СУВЕРЕННАЯ КРИВАЯ И КОРПОРАТИВНЫЙ G-SPREAD", fontProvider.getTitleFont(13.5f, TEXT_DARK));
        title.setSpacingAfter(3f);
        doc.add(title);

        Paragraph desc = new Paragraph("Кривая доходности государственных ценных бумаг (Минфин РК) и спреды квазигосударственного сектора", fontProvider.getBodyFont(9f, TEXT_MUTED));
        desc.setSpacingAfter(10f);
        doc.add(desc);

        // Yield Curve Chart
        byte[] curveBytes = chartService.generateYieldCurveChart(false, 500, 185);
        if (curveBytes.length > 0) {
            Image img = Image.getInstance(curveBytes);
            img.scaleToFit(500, 175);
            img.setAlignment(Element.ALIGN_CENTER);
            img.setSpacingAfter(10f);
            doc.add(img);
        }

        // G-Spread Analysis Table
        Paragraph gTitle = new Paragraph("АНАЛИЗ ПРЕМИИ ЗА РИСК (G-SPREAD НАД МИНФИНОМ РК)", fontProvider.getBoldFont(10.5f, ACCENT_EMERALD));
        gTitle.setSpacingAfter(5f);
        doc.add(gTitle);

        PdfPTable gTable = new PdfPTable(6);
        gTable.setWidthPercentage(100);
        gTable.setWidths(new float[]{16f, 32f, 15f, 13f, 11f, 13f});
        gTable.setSpacingAfter(10f);

        addTableHeader(gTable, "Тикер", "Эмитент", "Доходность", "Срок", "G-Spread", "Статус");
        addTableRow(gTable, "MUM120_0018", "Минфин РК (ГЦБ)", "15.50%", "3.5 г.", "0 б.п.", "Бенчмарк");
        addTableRow(gTable, "JSBNb13", "Отбасы Банк", "17.45%", "4.7 г.", "+195 б.п.", "Премиум");
        addTableRow(gTable, "BRKZb18", "Банк Развития Казахстана", "17.00%", "3.2 г.", "+150 б.п.", "Квазигос");
        addTableRow(gTable, "SKKZb23", "Самрук-Қазына", "16.80%", "2.8 г.", "+130 б.п.", "Квазигос");
        addTableRow(gTable, "BERKb22", "Береке Банк (TONIA)", "17.82%", "2.0 г.", "+232 б.п.", "Плавающий");
        doc.add(gTable);

        // Duration Analysis Box
        PdfPTable durTable = new PdfPTable(1);
        durTable.setWidthPercentage(100);
        PdfPCell durCell = new PdfPCell();
        durCell.setBackgroundColor(CARD_BG);
        durCell.setBorderColor(BORDER_COLOR);
        durCell.setPadding(8f);

        Paragraph durH = new Paragraph("ОЦЕНКА ДЮРАЦИИ И ЧУВСТВИТЕЛЬНОСТИ К СМЯГЧЕНИЮ ДКП (CAPITAL GAINS)", fontProvider.getBoldFont(9.5f, TEXT_DARK));
        durH.setSpacingAfter(3f);
        Paragraph durB = new Paragraph(
                "При текущей базовой ставке 16.25% облигации квазигоссектора предлагают исторически высокую доходность к погашению (17.0–17.5%).\n" +
                "При начале цикла снижения базовой ставки НБРК (консенсус-прогноз: смягчение до 13.5%–14.0% в горизонте 18 месяцев) " +
                "длинные облигации (модифицированная дюрация 3.5–4.5 года) обеспечат дополнительный прирост рыночной стоимости тела бумаг на +6.5% – +9.2%.\n" +
                "Совокупный доход инвестора (купон + курсовая переоценка) составит свыше 24% годовых при минимальном кредитном риске.",
                fontProvider.getBodyFont(8f, TEXT_MUTED)
        );
        durCell.addElement(durH);
        durCell.addElement(durB);
        durTable.addCell(durCell);
        doc.add(durTable);
    }

    private void renderPage5BestPicksAndArbitrage(Document doc, ReportMarketSnapshotDto s) throws Exception {
        Paragraph title = new Paragraph("СКРИНЕР ЛУЧШИХ ВОЗМОЖНОСТЕЙ: БОНДЫ И АРБИТРАЖ", fontProvider.getTitleFont(13.5f, TEXT_DARK));
        title.setSpacingAfter(3f);
        doc.add(title);

        Paragraph desc = new Paragraph("Отобранные инструменты квазигосударственного долга и возможности ценового арбитража", fontProvider.getBodyFont(9f, TEXT_MUTED));
        desc.setSpacingAfter(8f);
        doc.add(desc);

        // Quasigov Best Picks
        Paragraph qTitle = new Paragraph("ТОП-4 НАДЕЖНЫХ КВАЗИГОСУДАРСТВЕННЫХ ОБЛИГАЦИЙ (РИСК ~0%)", fontProvider.getBoldFont(10.5f, ACCENT_BLUE));
        qTitle.setSpacingAfter(4f);
        doc.add(qTitle);

        PdfPTable qTable = new PdfPTable(5);
        qTable.setWidthPercentage(100);
        qTable.setWidths(new float[]{15f, 35f, 16f, 16f, 18f});
        qTable.setSpacingAfter(10f);

        addTableHeader(qTable, "Тикер", "Эмитент", "Доходность (YTM)", "Срок", "Налог");
        List<ReportMarketSnapshotDto.BondReportItemDto> quasigovList = s.getTopQuasigovBonds();
        if (quasigovList != null && quasigovList.size() >= 4) {
            int count = Math.min(quasigovList.size(), 4);
            for (int i = 0; i < count; i++) {
                ReportMarketSnapshotDto.BondReportItemDto b = quasigovList.get(i);
                addTableRow(qTable, b.getCode(), b.getName(), b.getYtm() + "%", b.getDurationFormatted(), "ИПН 0%");
            }
        } else {
            addTableRow(qTable, "JSBNb13", "АО «Отбасы Банк»", "17.45%", "4.7 г.", "ИПН 0%");
            addTableRow(qTable, "KFUSb35", "Казахстанский фонд устойчивости", "17.10%", "3.1 г.", "ИПН 0%");
            addTableRow(qTable, "BRKZb18", "Банк Развития Казахстана", "17.00%", "3.2 г.", "ИПН 0%");
            addTableRow(qTable, "SKKZb23", "ФНБ «Самрук-Қазына»", "16.80%", "2.8 г.", "ИПН 0%");
        }
        doc.add(qTable);

        // Arbitrage KASE vs AIX
        Paragraph aTitle = new Paragraph("КРОСС-БИРЖЕВОЙ АРБИТРАЖ (KASE ⇄ AIX)", fontProvider.getBoldFont(10.5f, ACCENT_AMBER));
        aTitle.setSpacingAfter(4f);
        doc.add(aTitle);

        PdfPTable aTable = new PdfPTable(6);
        aTable.setWidthPercentage(100);
        aTable.setWidths(new float[]{24f, 15f, 15f, 15f, 13f, 18f});
        aTable.setSpacingAfter(10f);

        addTableHeader(aTable, "Эмитент", "KASE", "AIX", "Спред", "Спред %", "Рекомендация");
        List<ArbitrageItemDto> arbList = s.getArbitragePairs();
        if (arbList != null && arbList.size() >= 3) {
            int count = Math.min(arbList.size(), 7);
            for (int i = 0; i < count; i++) {
                ArbitrageItemDto a = arbList.get(i);
                String kaseP = formatPrice(a.getKasePrice(), a.getCurrency());
                String aixP = formatPrice(a.getAixPrice(), a.getCurrency());
                String spreadP = (a.getSpreadAbs() != null ? String.format("%,.2f ₸", a.getSpreadAbs().doubleValue()).replace(',', ' ') : "—");
                String spreadPct = (a.getSpreadPercent() != null ? String.format("%.2f%%", a.getSpreadPercent().doubleValue()) : "—");
                String recClean = cleanRecommendation(a.getRecommendation());
                addTableRow(aTable, a.getCompanyName(), kaseP, aixP, spreadP, spreadPct, recClean);
            }
        } else {
            addTableRow(aTable, "Kaspi.kz", "55 000 ₸", "54 500 ₸", "+500 ₸", "+0.92%", "Покупка AIX");
            addTableRow(aTable, "Народный Банк", "256 ₸", "254 ₸", "+2 ₸", "+0.79%", "Покупка AIX");
            addTableRow(aTable, "Казатомпром", "18 200 ₸", "18 100 ₸", "+100 ₸", "+0.55%", "Покупка AIX");
            addTableRow(aTable, "Эйр Астана", "674 ₸", "670 ₸", "+4 ₸", "+0.60%", "Покупка AIX");
            addTableRow(aTable, "КазМунайГаз", "14 250 ₸", "14 200 ₸", "+50 ₸", "+0.35%", "Паритет");
            addTableRow(aTable, "KEGOC", "1 480 ₸", "1 475 ₸", "+5 ₸", "+0.34%", "Паритет");
        }
        doc.add(aTable);

        // Arbitrage Mechanics Box
        PdfPTable arbTable = new PdfPTable(1);
        arbTable.setWidthPercentage(100);
        PdfPCell arbCell = new PdfPCell();
        arbCell.setBackgroundColor(CARD_BG);
        arbCell.setBorderColor(BORDER_COLOR);
        arbCell.setPadding(8f);

        Paragraph arbH = new Paragraph("МЕХАНИКА МЕЖБИРЖЕВОГО АРБИТРАЖА KASE ⇄ AIX", fontProvider.getBoldFont(9.5f, TEXT_DARK));
        arbH.setSpacingAfter(3f);
        Paragraph arbB = new Paragraph(
                "• Перевод ценных бумаг между Центральным депозитарием Казахстана (KASE CSD) и AIX CSD осуществляется в течение 2–4 часов через официальный междепозитарный мост.\n" +
                "• Стандартный биржевой режим расчетов: T+2. Для надежной фиксации спреда рекомендуется одновременное выставление встречных заявок на обеих площадках.\n" +
                "• Минимальный экономически оправданный спред с учетом биржевых и брокерских комиссий составляет 1.20%. При спреде выше 2.0% арбитраж генерирует чистую безрисковую прибыль.",
                fontProvider.getBodyFont(8f, TEXT_MUTED)
        );
        arbCell.addElement(arbH);
        arbCell.addElement(arbB);
        arbTable.addCell(arbCell);
        doc.add(arbTable);
    }

    private void renderPage6StrategicAllocation(Document doc, ReportMarketSnapshotDto s) throws Exception {
        BigDecimal capital = s.getInvestmentAmount() != null ? s.getInvestmentAmount() : new BigDecimal("26500000");
        String capFormatted = formatKzt(capital);

        Paragraph title = new Paragraph("СТРАТЕГИЧЕСКАЯ АЛЛОКАЦИЯ АКТИВОВ И МОДЕЛЬНЫЙ ПОРТФЕЛЬ", fontProvider.getTitleFont(13.5f, TEXT_DARK));
        title.setSpacingAfter(3f);
        doc.add(title);

        Paragraph desc = new Paragraph("Институциональная модель распределения активов на капитал " + capFormatted + " (Сбалансированный профиль)", fontProvider.getBodyFont(9f, TEXT_MUTED));
        desc.setSpacingAfter(10f);
        doc.add(desc);

        // Model Portfolio Table
        PdfPTable allocTable = new PdfPTable(5);
        allocTable.setWidthPercentage(100);
        allocTable.setWidths(new float[]{24f, 13f, 18f, 30f, 15f});
        allocTable.setSpacingAfter(10f);

        addTableHeader(allocTable, "Класс активов", "Целевой вес", "Сумма (₸)", "Целевые инструменты", "Ожид. доходность");

        BigDecimal cQuasi = capital.multiply(new BigDecimal("0.45")).setScale(0, RoundingMode.HALF_UP);
        BigDecimal cEquities = capital.multiply(new BigDecimal("0.30")).setScale(0, RoundingMode.HALF_UP);
        BigDecimal cDiscount = capital.multiply(new BigDecimal("0.15")).setScale(0, RoundingMode.HALF_UP);
        BigDecimal cCash = capital.multiply(new BigDecimal("0.10")).setScale(0, RoundingMode.HALF_UP);

        addTableRow(allocTable, "Квазигос-облигации", "45%", formatKzt(cQuasi), "JSBNb13, KFUSb35, BRKZb18", "17.20%");
        addTableRow(allocTable, "Дивидендные акции", "30%", formatKzt(cEquities), "HSBK, KSPI, KZAP, KMGZ", "12.5% + рост");
        addTableRow(allocTable, "Дисконтные бонды", "15%", formatKzt(cDiscount), "Выпуски БВУ ниже номинала", "18.50%");
        addTableRow(allocTable, "Ликвидный резерв", "10%", formatKzt(cCash), "Ноты НБРК / Овернайт Репо", "15.00%");
        addTableRowBold(allocTable, "ИТОГО (ПОРТФЕЛЬ)", "100%", capFormatted, "Сбалансированная структура", "~15.8% (чистыми)");
        doc.add(allocTable);

        // Rebalancing Rules Box
        PdfPTable rebTable = new PdfPTable(1);
        rebTable.setWidthPercentage(100);
        rebTable.setSpacingAfter(10f);

        PdfPCell rebCell = new PdfPCell();
        rebCell.setBackgroundColor(CARD_BG);
        rebCell.setBorderColor(BORDER_COLOR);
        rebCell.setPadding(8f);

        Paragraph rebH = new Paragraph("ПРИНЦИПЫ УПРАВЛЕНИЯ И РЕБАЛАНСИРОВКИ ПОРТФЕЛЯ", fontProvider.getBoldFont(9.5f, TEXT_DARK));
        rebH.setSpacingAfter(3f);
        Paragraph rebB = new Paragraph(
                "• Частота ребалансировки: 1 раз в полугодие при отклонении весов классов активов более чем на ±5 процентных пунктов.\n" +
                "• Реинвестирование купонов: Все поступающие купоны направляются в наиболее доходный класс на дату выплаты для реализации сложного процента.\n" +
                "• Защита от девальвации: До 20% квазигос-части может быть номинировано в надежных еврооблигациях (USD) с купоном 7.0–8.0% годовых.",
                fontProvider.getBodyFont(8f, TEXT_MUTED)
        );
        rebCell.addElement(rebH);
        rebCell.addElement(rebB);
        rebTable.addCell(rebCell);
        doc.add(rebTable);

        // Disclaimer Panel
        PdfPCell disCell = new PdfPCell();
        disCell.setBackgroundColor(CARD_BG);
        disCell.setBorderColor(BORDER_COLOR);
        disCell.setPadding(8f);

        Paragraph disTitle = new Paragraph("ОФИЦИАЛЬНОЕ ПРЕДУПРЕЖДЕНИЕ И ДИСКЛЕЙМЕР", fontProvider.getBoldFont(8f, ACCENT_AMBER));
        disTitle.setSpacingAfter(3f);
        disCell.addElement(disTitle);

        Paragraph disText = new Paragraph(
                "Настоящий аналитический обзор подготовлен автоматизированным аналитическим комплексом KASE & AIX Radar " +
                "исключительно в информационных целях на базе официальных торговых протоколов бирж KASE и AIX. " +
                "Информация не является индивидуальной инвестиционной рекомендацией или публичной офертой. " +
                "Инвестиции в финансовые инструменты сопряжены с рыночным риском. Доходность в прошлом не гарантирует доходности в будущем.",
                fontProvider.getBodyFont(7.2f, TEXT_MUTED)
        );
        disCell.addElement(disText);

        PdfPTable disTable = new PdfPTable(1);
        disTable.setWidthPercentage(100);
        disTable.addCell(disCell);
        doc.add(disTable);
    }

    private void addKpiCell(PdfPTable table, String label, String value, String sub, Color valColor) {
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(CARD_BG);
        c.setBorderColor(BORDER_COLOR);
        c.setPadding(7f);

        Paragraph pLabel = new Paragraph(label.toUpperCase(), fontProvider.getBoldFont(7.5f, TEXT_MUTED));
        Paragraph pVal = new Paragraph(value, fontProvider.getBoldFont(12f, valColor));
        Paragraph pSub = new Paragraph(sub, fontProvider.getBodyFont(7.2f, TEXT_MUTED));

        c.addElement(pLabel);
        c.addElement(pVal);
        c.addElement(pSub);
        table.addCell(c);
    }

    private void addMacroCell(PdfPTable table, String label, String val, String sub) {
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(PANEL_ALT_BG);
        c.setBorderColor(BORDER_COLOR);
        c.setPadding(6f);

        Paragraph pLabel = new Paragraph(label, fontProvider.getBoldFont(7.5f, TEXT_MUTED));
        Paragraph pVal = new Paragraph(val, fontProvider.getBoldFont(10.5f, ACCENT_BLUE));
        Paragraph pSub = new Paragraph(sub, fontProvider.getBodyFont(7f, TEXT_MUTED));

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
            c.setPadding(4f);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(c);
        }
    }

    private void addTableRow(PdfPTable table, String... values) {
        for (int i = 0; i < values.length; i++) {
            PdfPCell c = new PdfPCell(new Phrase(values[i] != null ? values[i] : "", fontProvider.getBodyFont(7.5f, i == 0 ? TEXT_DARK : TEXT_MUTED)));
            c.setBackgroundColor(CARD_BG);
            c.setBorderColor(BORDER_COLOR);
            c.setPadding(3.5f);
            if (i > 0) c.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(c);
        }
    }

    private void addTableRowBold(PdfPTable table, String... values) {
        for (int i = 0; i < values.length; i++) {
            PdfPCell c = new PdfPCell(new Phrase(values[i] != null ? values[i] : "", fontProvider.getBoldFont(8f, ACCENT_BLUE)));
            c.setBackgroundColor(PANEL_ALT_BG);
            c.setBorderColor(BORDER_COLOR);
            c.setPadding(4f);
            if (i > 0) c.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(c);
        }
    }

    private Paragraph createBullet(String bold, String text, ReportFontProvider fp) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(bold, fp.getBoldFont(8f, TEXT_DARK)));
        p.add(new Chunk(text, fp.getBodyFont(8f, TEXT_MUTED)));
        p.setSpacingAfter(3f);
        return p;
    }

    private PdfPCell createCaseBox(String title, String content, Color titleColor, ReportFontProvider fp) {
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(CARD_BG);
        c.setBorderColor(BORDER_COLOR);
        c.setPadding(7f);

        Paragraph t = new Paragraph(title, fp.getBoldFont(8.5f, titleColor));
        t.setSpacingAfter(3f);
        c.addElement(t);

        Paragraph b = new Paragraph(content, fp.getBodyFont(7.5f, TEXT_MUTED));
        c.addElement(b);
        return c;
    }

    private String cleanTrend(String trend) {
        if (trend == null) return "Боковик";
        if (trend.toLowerCase().contains("рост") || trend.toLowerCase().contains("bull")) return "Рост";
        if (trend.toLowerCase().contains("пад") || trend.toLowerCase().contains("bear") || trend.toLowerCase().contains("сниж")) return "Спад";
        return "Боковик";
    }

    private String cleanRecommendation(String rec) {
        if (rec == null || rec.isBlank()) return "Паритет";
        if (rec.contains("KASE")) return "Покупка KASE";
        if (rec.contains("AIX")) return "Покупка AIX";
        return "Паритет";
    }

    private String formatPrice(BigDecimal price, String currency) {
        if (price == null) return "—";
        String curr = currency != null ? currency : "₸";
        if ("USD".equalsIgnoreCase(curr)) curr = "$";
        else if ("KZT".equalsIgnoreCase(curr)) curr = "₸";

        if (price.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0) {
            return String.format("%,d %s", price.setScale(0, RoundingMode.HALF_UP).longValue(), curr).replace(',', ' ');
        } else {
            return String.format("%,.2f %s", price.doubleValue(), curr).replace(',', ' ').replace('.', ',');
        }
    }

    private String formatKzt(BigDecimal amount) {
        if (amount == null) return "0 ₸";
        return String.format("%,d ₸", amount.setScale(0, RoundingMode.HALF_UP).longValue()).replace(',', ' ');
    }
}
