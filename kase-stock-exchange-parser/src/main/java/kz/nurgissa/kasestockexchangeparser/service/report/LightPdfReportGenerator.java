package kz.nurgissa.kasestockexchangeparser.service.report;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import kz.nurgissa.kasestockexchangeparser.model.dtos.ReportMarketSnapshotDto;
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
public class LightPdfReportGenerator {

    private final ReportFontProvider fontProvider;

    private static final Color CARD_BG = Color.WHITE;
    private static final Color BORDER_LIGHT = new Color(226, 232, 240);
    private static final Color TEXT_DARK = new Color(15, 23, 42);
    private static final Color TEXT_MUTED = new Color(71, 85, 105);
    private static final Color COLOR_PRIMARY = new Color(37, 99, 235); // Blue
    private static final Color COLOR_SUCCESS = new Color(16, 185, 129); // Emerald
    private static final Color COLOR_WARNING = new Color(245, 158, 11); // Amber
    private static final Color COLOR_DANGER = new Color(239, 68, 68); // Red
    private static final Color TABLE_HEADER_BG = new Color(241, 245, 249);

    public byte[] generateLightReport(ReportMarketSnapshotDto snapshot) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setPageEvent(new ReportPageEventHelper(fontProvider, false, "ГИД ИНВЕСТОРА: ПАССИВНЫЙ ДОХОД В КАЗАХСТАНЕ"));

            doc.open();

            // ================= PAGE 1 =================
            renderPage1Cover(doc, snapshot);

            // ================= PAGE 2 =================
            doc.newPage();
            renderPage2AssetBattle(doc, snapshot);

            // ================= PAGE 3 =================
            doc.newPage();
            renderPage3TrafficLight(doc, snapshot);

            // ================= PAGE 4 =================
            doc.newPage();
            renderPage4PaycheckAndHowTo(doc, snapshot);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate LIGHT PDF report: {}", e.getMessage(), e);
            throw new RuntimeException("LIGHT PDF report generation failed", e);
        }
    }

    private void renderPage1Cover(Document doc, ReportMarketSnapshotDto s) throws Exception {
        // Banner Box
        PdfPTable banner = new PdfPTable(1);
        banner.setWidthPercentage(100);
        banner.setSpacingAfter(14f);

        PdfPCell bCell = new PdfPCell();
        bCell.setBackgroundColor(COLOR_PRIMARY);
        bCell.setPadding(14f);
        bCell.setBorder(Rectangle.NO_BORDER);

        Paragraph tag = new Paragraph("ГИД ЧАСТНОГО ИНВЕСТОРА В КАЗАХСТАНЕ  •  2026", fontProvider.getBoldFont(8.5f, new Color(191, 219, 254)));
        tag.setSpacingAfter(4f);
        bCell.addElement(tag);

        Paragraph mainTitle = new Paragraph("Пассивный доход на KASE и AIX:\nКак заставить деньги работать", fontProvider.getTitleFont(18f, Color.WHITE));
        mainTitle.setSpacingAfter(6f);
        bCell.addElement(mainTitle);

        Paragraph sub = new Paragraph("Пошаговое руководство для обычного человека: как надежно обогнать инфляцию, защитить тенге от девальвации и получать ежемесячный купонный доход с гарантированной ставкой налога 0%.", fontProvider.getBodyFont(9f, new Color(239, 246, 255)));
        bCell.addElement(sub);

        banner.addCell(bCell);
        doc.add(banner);

        // 3 Key Rules
        Paragraph kTitle = new Paragraph("3 ЗОЛОТЫХ ПРАВИЛА УМНОГО ИНВЕСТОРА В КАЗАХСТАНЕ", fontProvider.getBoldFont(10.5f, TEXT_DARK));
        kTitle.setSpacingAfter(6f);
        doc.add(kTitle);

        PdfPTable takeTable = new PdfPTable(3);
        takeTable.setWidthPercentage(100);
        takeTable.setSpacingAfter(12f);

        addTipCard(takeTable, "1. Налог 0% по закону", "Статья 341 Налогового кодекса РК полностью освобождает физлиц от ИПН с доходов по ценным бумагам на бирже KASE и AIX.", COLOR_SUCCESS);
        addTipCard(takeTable, "2. Ставка фиксируется", "В банке процент снизится уже через 6–12 месяцев, а по облигациям нацкомпаний ставка 17.5% гарантирована на 3–5 лет вперед.", COLOR_PRIMARY);
        addTipCard(takeTable, "3. Свобода капитала", "Вам не нужно ждать конца срока: продать облигацию можно в любой рабочий день, сохранив 100% накопленных купонных процентов.", COLOR_WARNING);
        doc.add(takeTable);

        // Core Market Numbers
        Paragraph sTitle = new Paragraph("МАСШТАБЫ КАЗАХСТАНСКОГО РЫНКА (KASE 2022–2026)", fontProvider.getBoldFont(10.5f, TEXT_DARK));
        sTitle.setSpacingAfter(6f);
        doc.add(sTitle);

        PdfPTable statTable = new PdfPTable(2);
        statTable.setWidthPercentage(100);
        statTable.setWidths(new float[]{60f, 40f});
        statTable.setSpacingAfter(12f);

        addSimpleRow(statTable, "Совокупный объем торгов акциями:", "2.13 ТРИЛЛИОНА тенге");
        addSimpleRow(statTable, "Количество совершенных сделок:", "8 423 997 сделок");
        addSimpleRow(statTable, "Самая народная бумага (Halyk Bank):", "2.33 млн сделок (каждая 4-я сделка в стране)");
        addSimpleRow(statTable, "Флагман национального рынка (КазМунайГаз):", "414.0 млрд ₸ биржевого оборота");
        doc.add(statTable);

        // Inflation vs Bonds Comparison
        Paragraph compTitle = new Paragraph("РЕАЛЬНАЯ ДОХОДНОСТЬ: ИНФЛЯЦИЯ ПРОТИВ ОБЛИГАЦИЙ", fontProvider.getBoldFont(10.5f, TEXT_DARK));
        compTitle.setSpacingAfter(6f);
        doc.add(compTitle);

        PdfPTable compTable = new PdfPTable(2);
        compTable.setWidthPercentage(100);
        compTable.setWidths(new float[]{50f, 50f});
        compTable.setSpacingAfter(12f);

        PdfPCell inflCell = new PdfPCell();
        inflCell.setBackgroundColor(new Color(254, 242, 242));
        inflCell.setBorderColor(new Color(254, 202, 202));
        inflCell.setPadding(8f);
        Paragraph inflH = new Paragraph("🔴 Инфляция в Казахстане: 8.6%", fontProvider.getBoldFont(9.5f, COLOR_DANGER));
        inflH.setSpacingAfter(3f);
        Paragraph inflB = new Paragraph("При хранении денег на карточке или дома покупательная способность падает на 8.6% в год. За 5 лет 10 000 000 ₸ теряют свыше 3.5 млн ₸ своей реальной покупательской силы.", fontProvider.getBodyFont(8f, TEXT_MUTED));
        inflCell.addElement(inflH);
        inflCell.addElement(inflB);
        compTable.addCell(inflCell);

        PdfPCell bondCell = new PdfPCell();
        bondCell.setBackgroundColor(new Color(236, 253, 245));
        bondCell.setBorderColor(new Color(167, 243, 208));
        bondCell.setPadding(8f);
        Paragraph bondH = new Paragraph("🟢 Облигации KASE Квазигос: 17.5%", fontProvider.getBoldFont(9.5f, COLOR_SUCCESS));
        bondH.setSpacingAfter(3f);
        Paragraph bondB = new Paragraph("Доходность квазигосударственных облигаций более чем в 2 раза опережает инфляцию. Реальная чистая доходность (Real Yield) превышает +8.9% годовых при минимальном риске.", fontProvider.getBodyFont(8f, TEXT_MUTED));
        bondCell.addElement(bondH);
        bondCell.addElement(bondB);
        compTable.addCell(bondCell);
        doc.add(compTable);

        // Tax Advantages Box
        PdfPTable taxTable = new PdfPTable(1);
        taxTable.setWidthPercentage(100);
        PdfPCell taxCell = new PdfPCell();
        taxCell.setBackgroundColor(new Color(239, 246, 255));
        taxCell.setBorderColor(new Color(191, 219, 254));
        taxCell.setPadding(8f);

        Paragraph taxH = new Paragraph("ПОЧЕМУ БИРЖА ВЫГОДНЕЕ ДРУГИХ АКТИВОВ (НАЛОГОВЫЕ ЛЬГОТЫ РК)", fontProvider.getBoldFont(9.5f, COLOR_PRIMARY));
        taxH.setSpacingAfter(3f);
        Paragraph taxB = new Paragraph(
                "• Биржа KASE & AIX: 0% налог на купоны и прирост стоимости ценных бумаг (ст. 341 Налогового кодекса РК).\n" +
                "• Аренда недвижимости: 10% ИПН или обязательная регистрация ИП с уплатой налогов и ежемесячных соцплатежей.\n" +
                "• Иностранные акции (Interactive Brokers): 10% налог с обязательной ежегодной сдачей декларации формы 240.00 / 270.00.",
                fontProvider.getBodyFont(8f, TEXT_DARK)
        );
        taxCell.addElement(taxH);
        taxCell.addElement(taxB);
        taxTable.addCell(taxCell);
        doc.add(taxTable);
    }

    private void renderPage2AssetBattle(Document doc, ReportMarketSnapshotDto s) throws Exception {
        ReportMarketSnapshotDto.AssetBattleComparisonDto b = s.getBattle();
        BigDecimal capital = s.getInvestmentAmount() != null ? s.getInvestmentAmount() : new BigDecimal("26500000");
        String capFormatted = formatKzt(capital);

        Paragraph title = new Paragraph("БИТВА ДОХОДНОСТЕЙ: КУДА ВЛОЖИТЬ ДЕНЬГИ В КАЗАХСТАНЕ?", fontProvider.getTitleFont(13.5f, TEXT_DARK));
        title.setSpacingAfter(3f);
        doc.add(title);

        Paragraph desc = new Paragraph("Сравнительный расчет реальной доходности на капитал " + capFormatted + " (рыночный бенчмарк)", fontProvider.getBodyFont(9f, TEXT_MUTED));
        desc.setSpacingAfter(10f);
        doc.add(desc);

        // 3 Cards
        PdfPTable battleTable = new PdfPTable(3);
        battleTable.setWidthPercentage(100);
        battleTable.setSpacingAfter(12f);

        String depIncome = b != null && b.getDepositAnnualIncome() != null ? formatKzt(b.getDepositAnnualIncome()) : formatKzt(capital.multiply(new BigDecimal("0.145")));
        String reIncome = b != null && b.getRealEstateNetAnnualIncome() != null ? formatKzt(b.getRealEstateNetAnnualIncome()) : formatKzt(capital.multiply(new BigDecimal("0.081")));
        String bondIncome = b != null && b.getBondAnnualIncome() != null ? formatKzt(b.getBondAnnualIncome()) : formatKzt(capital.multiply(new BigDecimal("0.1745")));

        // 1. Deposit
        PdfPCell c1 = createBattleCard(
                "Банковский депозит",
                (b != null ? b.getDepositRate() : "14.5") + "%",
                "+" + depIncome + "/год",
                "• Лимит КФГД — 10–20 млн ₸ (сумму выше нужно дробить по 2–3 банкам)\n" +
                "• Нацбанк уже начал снижение ставки (16.25%), банки вскоре снизят ГЭСВ\n" +
                "• На сберегательном вкладе деньги заблокированы: досрочный съем обнуляет %",
                BORDER_LIGHT,
                TEXT_DARK
        );
        battleTable.addCell(c1);

        // 2. Real Estate
        PdfPCell c2 = createBattleCard(
                "1-комн. квартира (Алматы)",
                (b != null ? b.getRealEstateNetYield() : "8.1") + "%",
                "+" + reIncome + "/год чистыми",
                "• Расчет по Krisha.kz: аренда минус 1 мес. простоя, ремонт, мебель и налоги\n" +
                "• Высокий порог входа (от 25–30 млн ₸ для ликвидного района)\n" +
                "• Низкая ликвидность: продажа занимает от 2 до 6 месяцев\n" +
                "• Нельзя быстро и частично забрать деньги",
                BORDER_LIGHT,
                TEXT_DARK
        );
        battleTable.addCell(c2);

        // 3. Quasigov Bonds (Winner)
        PdfPCell c3 = createBattleCard(
                "Облигации KASE (Квазигос)",
                (b != null ? b.getBondYield() : "17.45") + "%",
                "+" + bondIncome + "/год ЧИСТЫМИ",
                "• Лидер доходности: +" + (b != null && b.getBondAdvantageOverDeposit() != null ? formatKzt(b.getBondAdvantageOverDeposit()) : "доход") + " к депозиту!\n" +
                "• В 2.15 РАЗА ВЫГОДНЕЕ аренды квартиры!\n" +
                "• НАЛОГ 0% (ст. 341 НК РК) — чистый доход\n" +
                "• Ставка зафиксирована на 3–5 лет вперед\n" +
                "• Порог входа от 1 000 ₸, мгновенная продажа",
                COLOR_SUCCESS,
                COLOR_SUCCESS
        );
        battleTable.addCell(c3);
        doc.add(battleTable);

        // Detailed Comparison Matrix
        Paragraph mTitle = new Paragraph("СРАВНИТЕЛЬНАЯ МАТРИЦА ПО ВСЕМ КЛЮЧЕВЫМ ПАРАМЕТРАМ", fontProvider.getBoldFont(10.5f, TEXT_DARK));
        mTitle.setSpacingAfter(5f);
        doc.add(mTitle);

        PdfPTable matrixTable = new PdfPTable(4);
        matrixTable.setWidthPercentage(100);
        matrixTable.setWidths(new float[]{25f, 25f, 25f, 25f});
        matrixTable.setSpacingAfter(10f);

        addMatrixHeader(matrixTable, "Параметр сравнения", "Депозит в банке", "Квартира в Алматы", "Облигации KASE");
        addMatrixRow(matrixTable, "Чистая доходность", (b != null ? b.getDepositRate() : "14.5") + "% годовых", (b != null ? b.getRealEstateNetYield() : "8.1") + "% (чистыми)", (b != null ? b.getBondYield() : "17.45") + "% (чистыми)");
        addMatrixRow(matrixTable, "Годовой доход (" + capFormatted + ")", "+" + depIncome, "+" + reIncome, "+" + bondIncome);
        addMatrixRow(matrixTable, "Налог на прибыль (ИПН)", "0% (пока действует льгота)", "10% (ИП / патент / декларация)", "0% (ст. 341 НК РК навсегда)");
        addMatrixRow(matrixTable, "Ликвидность капитала", "С потерей % (сберегательный)", "Низкая (продажа 2–6 мес.)", "Высокая (1 день на бирже)");
        addMatrixRow(matrixTable, "Минимальный порог входа", "от 1 000 ₸", "от 25 000 000 ₸", "от 1 000 ₸ (1 облигация)");
        addMatrixRow(matrixTable, "Пассивность процесса", "100% пассивно", "Требует жильцов, ремонт, ЖКХ", "100% пассивно (автовыплата)");
        addMatrixRow(matrixTable, "Гарантия сохранности", "КФГД до 10–20 млн ₸", "Риск износа здания / района", "Гарантия нацхолдингов / Минфина");
        doc.add(matrixTable);

        // Summary Verdict Box
        PdfPTable vTable = new PdfPTable(1);
        vTable.setWidthPercentage(100);
        PdfPCell vCell = new PdfPCell();
        vCell.setBackgroundColor(new Color(236, 253, 245));
        vCell.setBorderColor(COLOR_SUCCESS);
        vCell.setPadding(8f);

        Paragraph vTitle = new Paragraph("ИТОГОВЫЙ ВЕРДИКТ НЕЗАВИСИМОГО АНАЛИТИКА", fontProvider.getBoldFont(9.5f, COLOR_SUCCESS));
        vTitle.setSpacingAfter(3f);
        vCell.addElement(vTitle);

        String advVsRe = b != null && b.getBondAnnualIncome() != null && b.getRealEstateNetAnnualIncome() != null
                ? formatKzt(b.getBondAnnualIncome().subtract(b.getRealEstateNetAnnualIncome()))
                : "2.47 млн ₸";

        Paragraph vText = new Paragraph(
                "При равном объеме инвестиций (" + capFormatted + ") квазигосударственные облигации Казахстана " +
                "приносят на " + (b != null && b.getBondAdvantageOverDeposit() != null ? formatKzt(b.getBondAdvantageOverDeposit()) : "780 000 ₸") + " в год БОЛЬШЕ депозита и на " + advVsRe + " в год БОЛЬШЕ аренды квартиры в Алматы, " +
                "не требуя ремонта, рекламы, поиска жильцов и оплаты коммунальных услуг. Ваш капитал сохраняет 100% ликвидность с ежедневным доступом к деньгам.",
                fontProvider.getBodyFont(8f, TEXT_DARK)
        );
        vCell.addElement(vText);
        vTable.addCell(vCell);
        doc.add(vTable);
    }

    private void renderPage3TrafficLight(Document doc, ReportMarketSnapshotDto s) throws Exception {
        Paragraph title = new Paragraph("СВЕТОФОР НАДЕЖНОСТИ: КАК ИНВЕСТИРОВАТЬ БЕЗ СТРАХА", fontProvider.getTitleFont(13.5f, TEXT_DARK));
        title.setSpacingAfter(3f);
        doc.add(title);

        Paragraph desc = new Paragraph("Классификация ценных бумаг на казахстанской бирже по уровню инвестиционного риска и доходности", fontProvider.getBodyFont(9f, TEXT_MUTED));
        desc.setSpacingAfter(10f);
        doc.add(desc);

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{50f, 50f});
        table.setSpacingAfter(12f);

        // 1. Green
        PdfPCell greenCell = createTrafficCard(
                "🟢 ЗЕЛЕНЫЙ СЕКТОР (Вместо депозита)",
                "Доходность: 15.5% – 17.5% годовых  |  Риск: Минимальный (~0%)",
                "• Инструменты: Минфин РК (ГЦБ), Отбасы Банк (JSBNb13), БРК (BRKZb18), Самрук-Қазына (SKKZb23).\n" +
                "• Гарантия: Государство и крупнейшие квазигосударственные институты развития РК.\n" +
                "• Кому подходит: Для сохранения подушки безопасности и получения гарантированного дохода без риска.",
                COLOR_SUCCESS
        );
        table.addCell(greenCell);

        // 2. Blue
        PdfPCell blueCell = createTrafficCard(
                "🔵 СИНИЙ СЕКТОР (Дивидендные чемпионы)",
                "Дивиденды: 10% – 14% + рост курса  |  Риск: Умеренный",
                "• Инструменты: Акции Halyk Bank (HSBK), Kaspi.kz (KSPI), Казатомпрома (KZAP), КазМунайГаза (KMGZ).\n" +
                "• Преимущество: Регулярные дивиденды поступают живыми деньгами на счет + защита от девальвации.\n" +
                "• Кому подходит: Долгосрочным инвесторам для формирования пенсионного семейного капитала.",
                COLOR_PRIMARY
        );
        table.addCell(blueCell);

        // 3. Yellow
        PdfPCell yellowCell = createTrafficCard(
                "🟡 ЖЕЛТЫЙ СЕКТОР (Облигации со скидкой / Дисконт)",
                "Доходность к погашению: 18.0% – 22.0% годовых",
                "• Инструменты: Выпуски надежных БВУ и корпораций, которые торгуются дешевле 95% от номинала.\n" +
                "• Двойная выгода: Инвестор получает регулярный купон + гарантированную прибыль при возврате 100% номинала.\n" +
                "• Кому подходит: Инвесторам, желающим максимизировать отдачу от вложений.",
                COLOR_WARNING
        );
        table.addCell(yellowCell);

        // 4. Red
        PdfPCell redCell = createTrafficCard(
                "🔴 КРАСНЫЙ СЕКТОР (Зона риска / Неликвид)",
                "Малоликвидные бумаги без маркетмейкеров",
                "• Инструменты: Акции и облигации третьего эшелона, где сделки проходят раз в несколько недель.\n" +
                "• Главная опасность: Сложно продать бумаги по справедливой цене, широкий спред между покупкой и продажей.\n" +
                "• Правило для новичка: Не вкладывать средства в бумаги без активного маркетмейкера KASE!",
                COLOR_DANGER
        );
        table.addCell(redCell);
        doc.add(table);

        // Starter Portfolio Table
        Paragraph portTitle = new Paragraph("СТАРТОВЫЙ ПОРТФЕЛЬ НАДЕЖНОСТИ ДЛЯ НАЧИНАЮЩЕГО ИНВЕСТОРА", fontProvider.getBoldFont(10.5f, TEXT_DARK));
        portTitle.setSpacingAfter(5f);
        doc.add(portTitle);

        PdfPTable portTable = new PdfPTable(6);
        portTable.setWidthPercentage(100);
        portTable.setWidths(new float[]{14f, 32f, 15f, 15f, 12f, 12f});
        portTable.setSpacingAfter(10f);

        addMatrixHeader(portTable, "Тикер", "Эмитент", "Доходность (YTM)", "Выплаты купона", "Срок", "Рейтинг");
        addMatrixRow(portTable, "JSBNb13", "АО «Отбасы Банк»", "17.45%", "2 раза в год", "4.7 года", "Квазигос");
        addMatrixRow(portTable, "KFUSb35", "Казахстанский фонд устойчивости", "17.10%", "4 раза в год", "3.1 года", "НБ РК");
        addMatrixRow(portTable, "BRKZb18", "Банк Развития Казахстана", "17.00%", "2 раза в год", "3.2 года", "Байтерек");
        addMatrixRow(portTable, "SKKZb23", "ФНБ «Самрук-Қазына»", "16.80%", "2 раза в год", "2.8 года", "Суверенный");
        addMatrixRow(portTable, "KZTKb3", "АО «Казахтелеком»", "16.50%", "2 раза в год", "2.0 года", "AAA (kz)");
        doc.add(portTable);

        // Ladder Strategy Tip
        PdfPTable ladTable = new PdfPTable(1);
        ladTable.setWidthPercentage(100);
        PdfPCell ladCell = new PdfPCell();
        ladCell.setBackgroundColor(new Color(248, 250, 252));
        ladCell.setBorderColor(BORDER_LIGHT);
        ladCell.setPadding(8f);

        Paragraph ladH = new Paragraph("💡 СТРАТЕГИЯ СТУПЕНЧАТОЙ «ЛЕСЕНКИ» (BOND LADDER)", fontProvider.getBoldFont(9.5f, COLOR_PRIMARY));
        ladH.setSpacingAfter(3f);
        Paragraph ladB = new Paragraph(
                "Разделите капитал на 4 равные части со сроком погашения через 1, 2, 3 и 5 лет. " +
                "Каждый год часть облигаций будет гаситься, возвращая вам 100% вложенных денег для реинвестирования или крупных покупок. " +
                "При этом остальная часть портфеля продолжит приносить гарантированные 17%+ годовых даже при снижении ставок Нацбанком.",
                fontProvider.getBodyFont(8f, TEXT_MUTED)
        );
        ladCell.addElement(ladH);
        ladCell.addElement(ladB);
        ladTable.addCell(ladCell);
        doc.add(ladTable);
    }

    private void renderPage4PaycheckAndHowTo(Document doc, ReportMarketSnapshotDto s) throws Exception {
        BigDecimal capital = s.getInvestmentAmount() != null ? s.getInvestmentAmount() : new BigDecimal("26500000");

        Paragraph title = new Paragraph("КАЛЕНДАРЬ КУПОННОЙ ЗАРПЛАТЫ НА 12 МЕСЯЦЕВ", fontProvider.getTitleFont(13.5f, TEXT_DARK));
        title.setSpacingAfter(3f);
        doc.add(title);

        Paragraph desc = new Paragraph("Готовая корзина из 4-х надежных бумаг со смещенными датами выплат: живой денежный поток каждый месяц года", fontProvider.getBodyFont(9f, TEXT_MUTED));
        desc.setSpacingAfter(10f);
        doc.add(desc);

        // 12 Months Grid
        PdfPTable calTable = new PdfPTable(6);
        calTable.setWidthPercentage(100);
        calTable.setSpacingAfter(12f);

        List<ReportMarketSnapshotDto.CouponMonthDto> calendar = s.getPaycheck12Months();
        if (calendar == null || calendar.size() < 12) {
            calendar = buildFallbackCalendar(capital);
        }
        for (ReportMarketSnapshotDto.CouponMonthDto m : calendar) {
            PdfPCell mCell = new PdfPCell();
            mCell.setBackgroundColor(new Color(241, 245, 249));
            mCell.setBorderColor(BORDER_LIGHT);
            mCell.setPadding(6f);

            Paragraph mName = new Paragraph(m.getMonthName() + " (Месяц " + m.getMonthNumber() + ")", fontProvider.getBoldFont(8.5f, COLOR_PRIMARY));
            Paragraph mCode = new Paragraph(m.getIssuerCode(), fontProvider.getBoldFont(8f, TEXT_DARK));
            Paragraph mPay = new Paragraph("+" + formatKzt(m.getPayoutAmount()), fontProvider.getBoldFont(8.5f, COLOR_SUCCESS));

            mCell.addElement(mName);
            mCell.addElement(mCode);
            mCell.addElement(mPay);
            calTable.addCell(mCell);
        }
        doc.add(calTable);

        // USD Shield Section
        Paragraph usdTitle = new Paragraph("🛡️ ДОЛЛАРОВЫЙ ЩИТ: 8.0% В USD ПРОТИВ 1.0% В БАНКЕ", fontProvider.getBoldFont(10.5f, COLOR_PRIMARY));
        usdTitle.setSpacingAfter(4f);
        doc.add(usdTitle);

        PdfPTable usdTable = new PdfPTable(1);
        usdTable.setWidthPercentage(100);
        usdTable.setSpacingAfter(12f);

        PdfPCell uCell = new PdfPCell();
        uCell.setBackgroundColor(new Color(239, 246, 255));
        uCell.setBorderColor(new Color(191, 219, 254));
        uCell.setPadding(8f);

        Paragraph uText = new Paragraph(
                "• Валютный депозит в казахстанских банках дает максимум 1.0% годовых в долларах США (ограничение КФГД).\n" +
                "• На бирже KASE обращаются надежные государственные и квазигосударственные еврооблигации (Минфин РК, Самрук, КМГ) с купоном от 6.5% до 8.5% годовых в USD!\n" +
                "• Это в 7–8 раз выгоднее банковского депозита, полностью защищает от девальвации тенге и также освобождено от налога (ИПН 0%).",
                fontProvider.getBodyFont(8f, TEXT_DARK)
        );
        uCell.addElement(uText);
        usdTable.addCell(uCell);
        doc.add(usdTable);

        // 3 Simple Steps
        Paragraph howTitle = new Paragraph("КАК НАЧАТЬ ИНВЕСТИРОВАТЬ ЗА 3 ПРОСТЫХ ШАГА", fontProvider.getBoldFont(10.5f, TEXT_DARK));
        howTitle.setSpacingAfter(4f);
        doc.add(howTitle);

        PdfPTable stepTable = new PdfPTable(3);
        stepTable.setWidthPercentage(100);
        stepTable.setSpacingAfter(10f);
        addStepCard(stepTable, "Шаг 1: Счет онлайн за 3 мин.", "Откройте брокерский счет прямо в приложении Halyk, Freedom Broker, BCC Trade или Jusan. Это бесплатно, по Face ID.", fontProvider);
        addStepCard(stepTable, "Шаг 2: Пополнение без %", "Пополните брокерский счет с любой карты казахстанского банка без комиссий через стандартный перевод в приложении.", fontProvider);
        addStepCard(stepTable, "Шаг 3: Покупка в 1 клик", "Найдите нужный тикер (например, JSBNb13) и нажмите «Купить». Купонные выплаты будут падать на карту автоматически!", fontProvider);
        doc.add(stepTable);

        // Investor FAQ Box
        Paragraph faqTitle = new Paragraph("ЧАСТО ЗАДАВАЕМЫЕ ВОПРОСЫ НАЧИНАЮЩИХ (FAQ)", fontProvider.getBoldFont(10.5f, TEXT_DARK));
        faqTitle.setSpacingAfter(4f);
        doc.add(faqTitle);

        PdfPTable faqTable = new PdfPTable(3);
        faqTable.setWidthPercentage(100);
        addFaqCard(faqTable, "Что при дефолте эмитента?", "Квазигосударственные облигации выпущены структурами с суверенной поддержкой РК (Самрук, Отбасы, БРК). Дефолт квазигоссектора исключен без дефолта всей финансовой системы страны.");
        addFaqCard(faqTable, "Можно забрать деньги раньше?", "Да! Облигации продаются в 1 клик в рабочие часы биржи. Вы забираете всю сумму плюс 100% накопленного купона за каждый фактический день владения (НКД не сгорает).");
        addFaqCard(faqTable, "Есть ли скрытые комиссии?", "Нет скрытых комиссий. Брокер берет лишь разовую биржевую комиссию от 0.05% до 0.1% за сделку. Зачисление купонов на ваш банковский счет бесплатно.");
        doc.add(faqTable);
    }

    private void addTipCard(PdfPTable table, String title, String text, Color accentColor) {
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(CARD_BG);
        c.setBorderColor(BORDER_LIGHT);
        c.setPadding(7f);

        Paragraph t = new Paragraph(title, fontProvider.getBoldFont(8.5f, accentColor));
        t.setSpacingAfter(3f);
        c.addElement(t);

        Paragraph b = new Paragraph(text, fontProvider.getBodyFont(7.5f, TEXT_MUTED));
        c.addElement(b);
        table.addCell(c);
    }

    private void addSimpleRow(PdfPTable table, String title, String val) {
        PdfPCell c1 = new PdfPCell(new Phrase(title, fontProvider.getBodyFont(8.5f, TEXT_MUTED)));
        c1.setBackgroundColor(CARD_BG);
        c1.setBorderColor(BORDER_LIGHT);
        c1.setPadding(4f);

        PdfPCell c2 = new PdfPCell(new Phrase(val, fontProvider.getBoldFont(9f, COLOR_PRIMARY)));
        c2.setBackgroundColor(CARD_BG);
        c2.setBorderColor(BORDER_LIGHT);
        c2.setPadding(4f);
        c2.setHorizontalAlignment(Element.ALIGN_RIGHT);

        table.addCell(c1);
        table.addCell(c2);
    }

    private PdfPCell createBattleCard(String name, String rate, String income, String points, Color borderColor, Color rateColor) {
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(CARD_BG);
        c.setBorderColor(borderColor);
        c.setBorderWidth(borderColor.equals(COLOR_SUCCESS) ? 1.5f : 1.0f);
        c.setPadding(7f);

        Paragraph pName = new Paragraph(name, fontProvider.getBoldFont(8.5f, TEXT_DARK));
        Paragraph pRate = new Paragraph(rate, fontProvider.getTitleFont(13f, rateColor));
        Paragraph pInc = new Paragraph(income, fontProvider.getBoldFont(9f, rateColor));
        pInc.setSpacingAfter(4f);

        c.addElement(pName);
        c.addElement(pRate);
        c.addElement(pInc);

        Paragraph pts = new Paragraph(points, fontProvider.getBodyFont(7.2f, TEXT_MUTED));
        c.addElement(pts);
        return c;
    }

    private void addMatrixHeader(PdfPTable table, String... headers) {
        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h, fontProvider.getBoldFont(8f, TEXT_DARK)));
            c.setBackgroundColor(TABLE_HEADER_BG);
            c.setBorderColor(BORDER_LIGHT);
            c.setPadding(4f);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(c);
        }
    }

    private void addMatrixRow(PdfPTable table, String... values) {
        for (int i = 0; i < values.length; i++) {
            PdfPCell c = new PdfPCell(new Phrase(values[i] != null ? values[i] : "", fontProvider.getBodyFont(7.5f, i == 0 ? TEXT_DARK : TEXT_MUTED)));
            c.setBackgroundColor(CARD_BG);
            c.setBorderColor(BORDER_LIGHT);
            c.setPadding(3.5f);
            if (i > 0) c.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(c);
        }
    }

    private PdfPCell createTrafficCard(String title, String subtitle, String content, Color accent) {
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(CARD_BG);
        c.setBorderColor(BORDER_LIGHT);
        c.setPadding(7f);

        Paragraph t = new Paragraph(title, fontProvider.getBoldFont(8.5f, accent));
        Paragraph sub = new Paragraph(subtitle, fontProvider.getBoldFont(7.5f, TEXT_DARK));
        sub.setSpacingAfter(3f);
        Paragraph body = new Paragraph(content, fontProvider.getBodyFont(7.2f, TEXT_MUTED));

        c.addElement(t);
        c.addElement(sub);
        c.addElement(body);
        return c;
    }

    private void addStepCard(PdfPTable table, String title, String desc, ReportFontProvider fp) {
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(CARD_BG);
        c.setBorderColor(BORDER_LIGHT);
        c.setPadding(7f);

        Paragraph t = new Paragraph(title, fp.getBoldFont(8.5f, COLOR_PRIMARY));
        t.setSpacingAfter(2f);
        Paragraph d = new Paragraph(desc, fp.getBodyFont(7.5f, TEXT_MUTED));

        c.addElement(t);
        c.addElement(d);
        table.addCell(c);
    }

    private void addFaqCard(PdfPTable table, String question, String answer) {
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(CARD_BG);
        c.setBorderColor(BORDER_LIGHT);
        c.setPadding(6f);

        Paragraph q = new Paragraph(question, fontProvider.getBoldFont(8f, TEXT_DARK));
        q.setSpacingAfter(2f);
        Paragraph a = new Paragraph(answer, fontProvider.getBodyFont(7.2f, TEXT_MUTED));

        c.addElement(q);
        c.addElement(a);
        table.addCell(c);
    }

    private List<ReportMarketSnapshotDto.CouponMonthDto> buildFallbackCalendar(BigDecimal capital) {
        String[] monthNames = {"Янв", "Фев", "Мар", "Апр", "Май", "Июн", "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек"};
        String[] issuers = {"Отбасы Банк", "КФУ (НБ РК)", "Банк Развития (БРК)", "Самрук-Қазына"};
        String[] codes = {"JSBNb13", "KFUSb35", "BRKZb18", "SKKZb23"};

        List<ReportMarketSnapshotDto.CouponMonthDto> list = new java.util.ArrayList<>();
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

    private String formatKzt(BigDecimal amount) {
        if (amount == null) return "0 ₸";
        return String.format("%,d ₸", amount.setScale(0, RoundingMode.HALF_UP).longValue()).replace(',', ' ');
    }
}
