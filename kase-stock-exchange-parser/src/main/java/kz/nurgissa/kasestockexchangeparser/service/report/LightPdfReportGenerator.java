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

    private static final Color BG_LIGHT = new Color(248, 250, 252);
    private static final Color CARD_BG = Color.WHITE;
    private static final Color BORDER_LIGHT = new Color(226, 232, 240);
    private static final Color TEXT_DARK = new Color(15, 23, 42);
    private static final Color TEXT_MUTED = new Color(71, 85, 105);
    private static final Color COLOR_PRIMARY = new Color(37, 99, 235); // Blue
    private static final Color COLOR_SUCCESS = new Color(16, 185, 129); // Emerald
    private static final Color COLOR_WARNING = new Color(245, 158, 11); // Amber
    private static final Color COLOR_DANGER = new Color(239, 68, 68); // Red

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
        banner.setSpacingAfter(18f);

        PdfPCell bCell = new PdfPCell();
        bCell.setBackgroundColor(COLOR_PRIMARY);
        bCell.setPadding(18f);
        bCell.setBorder(Rectangle.NO_BORDER);

        Paragraph tag = new Paragraph("ГИД ИНВЕСТОРА В КАЗАХСТАНЕ  •  СЕНТЯБРЬ 2026", fontProvider.getBoldFont(9f, new Color(191, 219, 254)));
        tag.setSpacingAfter(4f);
        bCell.addElement(tag);

        Paragraph mainTitle = new Paragraph("Пассивный доход на KASE и AIX:\nКак заставить деньги работать", fontProvider.getTitleFont(20f, Color.WHITE));
        mainTitle.setSpacingAfter(8f);
        bCell.addElement(mainTitle);

        Paragraph sub = new Paragraph("Пошаговое руководство для обычного человека: как обогнать инфляцию, защитить тенге и получать купонную зарплату каждый месяц с налогом 0%.", fontProvider.getBodyFont(10f, new Color(239, 246, 255)));
        bCell.addElement(sub);

        banner.addCell(bCell);
        doc.add(banner);

        // 3 Key Takeaways
        Paragraph kTitle = new Paragraph("3 ГЛАВНЫХ ПРАВИЛА УМНОГО ИНВЕСТОРА В КАЗАХСТАНЕ", fontProvider.getBoldFont(11f, TEXT_DARK));
        kTitle.setSpacingAfter(8f);
        doc.add(kTitle);

        PdfPTable takeTable = new PdfPTable(3);
        takeTable.setWidthPercentage(100);
        takeTable.setSpacingAfter(16f);

        addTipCard(takeTable, "1. Налог 0% по закону", "Статья 341 Налогового кодекса РК освобождает физлиц от подоходного налога с доходов по ценным бумагам на бирже KASE и AIX.", COLOR_SUCCESS);
        addTipCard(takeTable, "2. Ставка фиксируется", "В банке процент снизится через 6 месяцев, а по облигациям нацкомпаний ставка 17.5% гарантирована на 3-5 лет вперед.", COLOR_PRIMARY);
        addTipCard(takeTable, "3. Свобода денег", "Вам не нужно ждать год: продать облигацию можно в любой рабочий день, забрав 100% накопленных процентов.", COLOR_WARNING);
        doc.add(takeTable);

        // Core Market Numbers Table
        Paragraph sTitle = new Paragraph("МАСШТАБЫ КАЗАХСТАНСКОГО РЫНКА (KASE 2022–2026)", fontProvider.getBoldFont(11f, TEXT_DARK));
        sTitle.setSpacingAfter(6f);
        doc.add(sTitle);

        PdfPTable statTable = new PdfPTable(2);
        statTable.setWidthPercentage(100);
        statTable.setWidths(new float[]{60f, 40f});

        addSimpleRow(statTable, "Совокупный объем торгов акциями:", "2.13 ТРИЛЛИОНА тенге");
        addSimpleRow(statTable, "Количество совершенных сделок:", "8 423 997 сделок");
        addSimpleRow(statTable, "Самая народная бумага (Halyk Bank):", "2.33 млн сделок (каждая 4-я сделка)");
        addSimpleRow(statTable, "Крупнейшая компания (КазМунайГаз):", "414 млрд ₸ биржевого оборота");
        doc.add(statTable);
    }

    private void renderPage2AssetBattle(Document doc, ReportMarketSnapshotDto s) throws Exception {
        Paragraph title = new Paragraph("БИТВА ДОХОДНОСТЕЙ: КУДА ВЛОЖИТЬ ДЕНЬГИ В КАЗАХСТАНЕ?", fontProvider.getTitleFont(14f, TEXT_DARK));
        title.setSpacingAfter(4f);
        doc.add(title);

        Paragraph desc = new Paragraph("Сравнение на капитал 26 500 000 ₸ (реальная медианная стоимость 1-комн. квартиры в Алматы по Krisha.kz)", fontProvider.getBodyFont(9.5f, TEXT_MUTED));
        desc.setSpacingAfter(12f);
        doc.add(desc);

        ReportMarketSnapshotDto.AssetBattleComparisonDto b = s.getBattle();

        PdfPTable battleTable = new PdfPTable(3);
        battleTable.setWidthPercentage(100);
        battleTable.setSpacingAfter(16f);

        // 1. Deposit
        PdfPCell c1 = createBattleCard(
                "Банковский депозит",
                (b != null ? b.getDepositRate() : "14.5") + "%",
                "+" + (b != null ? String.format("%,d", b.getDepositAnnualIncome().longValue()) : "3 842 500") + " ₸/год",
                "• Сумма 26.5 млн выше гарантии КФГД (нужно дробить по 3 банкам)\n" +
                "• Банки снижают ставки вслед за Нацбанком (16.25%)\n" +
                "• На сберегательном вкладе деньги заморожены: досрочное снятие сжигает проценты",
                BORDER_LIGHT,
                TEXT_DARK
        );
        battleTable.addCell(c1);

        // 2. Real Estate (Krisha.kz)
        PdfPCell c2 = createBattleCard(
                "1-комн. квартира (Алматы)",
                (b != null ? b.getRealEstateNetYield() : "8.1") + "%",
                "+" + (b != null ? String.format("%,d", b.getRealEstateNetAnnualIncome().longValue()) : "2 150 000") + " ₸/год чистыми",
                "• Покупка: 26.5 млн ₸ (Krisha.kz)\n" +
                "• Аренда: 230 000 ₸/мес (2.76 млн/год) минус простой, ремонт, мебель и налоги\n" +
                "• Огромный порог входа (26.5 млн ₸)\n" +
                "• Нельзя быстро продать часть квартиры",
                BORDER_LIGHT,
                TEXT_DARK
        );
        battleTable.addCell(c2);

        // 3. KASE Quasigov Bonds (Winner)
        PdfPCell c3 = createBattleCard(
                "Облигации KASE (Квазигос)",
                (b != null ? b.getBondYield() : "17.45") + "%",
                "+" + (b != null ? String.format("%,d", b.getBondAnnualIncome().longValue()) : "4 624 250") + " ₸/год ЧИСТЫМИ",
                "• +781 750 ₸ к депозиту в банке!\n" +
                "• В 2.15 РАЗА ВЫГОДНЕЕ сдачи квартиры в аренду!\n" +
                "• НАЛОГ 0% (ст. 341 НК РК)\n" +
                "• Ставка зафиксирована на 3-5 лет вперед (Отбасы, БРК, Самрук)\n" +
                "• Порог входа от 1 000 ₸, а не 26.5 млн ₸!",
                COLOR_SUCCESS,
                COLOR_SUCCESS
        );
        battleTable.addCell(c3);

        doc.add(battleTable);

        // Summary Verdict Box
        PdfPTable vTable = new PdfPTable(1);
        vTable.setWidthPercentage(100);
        PdfPCell vCell = new PdfPCell();
        vCell.setBackgroundColor(new Color(236, 253, 245));
        vCell.setBorderColor(COLOR_SUCCESS);
        vCell.setPadding(10f);

        Paragraph vTitle = new Paragraph("ИТОГОВЫЙ ВЕРДИКТ АНАЛИТИКА", fontProvider.getBoldFont(10f, COLOR_SUCCESS));
        vTitle.setSpacingAfter(4f);
        vCell.addElement(vTitle);

        Paragraph vText = new Paragraph(
                "При том же самом капитале (26.5 млн тенге) государственные и квазигосударственные облигации Казахстана " +
                "приносят на 2.47 миллиона тенге в год БОЛЬШЕ, чем аренда однокомнатной квартиры в Алматы, " +
                "не требуя ремонта, поиска жильцов и оплаты коммунальных услуг. При этом ваш капитал остается 100% ликвидным.",
                fontProvider.getBodyFont(9f, TEXT_DARK)
        );
        vCell.addElement(vText);
        vTable.addCell(vCell);
        doc.add(vTable);
    }

    private void renderPage3TrafficLight(Document doc, ReportMarketSnapshotDto s) throws Exception {
        Paragraph title = new Paragraph("СВЕТОФОР НАДЕЖНОСТИ: КАК ИНВЕСТИРОВАТЬ БЕЗ СТРАХА", fontProvider.getTitleFont(14f, TEXT_DARK));
        title.setSpacingAfter(4f);
        doc.add(title);

        Paragraph desc = new Paragraph("Классификация ценных бумаг на казахстанской бирже по уровню риска и доходности", fontProvider.getBodyFont(9.5f, TEXT_MUTED));
        desc.setSpacingAfter(12f);
        doc.add(desc);

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingAfter(14f);

        // 1. Green
        PdfPCell greenCell = createTrafficCard(
                "🟢 ЗЕЛЕНЫЙ СЕКТОР (Вместо депозита)",
                "Доходность: 15.5% – 17.5% годовых  |  Риск: ~0%",
                "• Инструменты: Облигации Минфина РК (ГЦБ), Отбасы Банк (JSBNb13), Банк Развития Казахстана (BRKZb18), Самрук-Қазына (SKKZb23).\n" +
                "• Гарантия: Государство и крупнейшие квазигосударственные холдинги.\n" +
                "• Кому подходит: Всем, кто хочет сохранить подушку безопасности с максимальным доходом.",
                COLOR_SUCCESS
        );
        table.addCell(greenCell);

        // 2. Blue
        PdfPCell blueCell = createTrafficCard(
                "🔵 СИНИЙ СЕКТОР (Дивидендные чемпионы)",
                "Дивиденды: 10% – 14% + рост акций  |  Риск: Умеренный",
                "• Инструменты: Акции Народного Банка (HSBK), Kaspi.kz (KSPI), Казатомпрома (KZAP), КазМунайГаза (KMGZ).\n" +
                "• Преимущество: Регулярные дивиденды падают живыми деньгами на карту + защита от девальвации за счет роста бизнеса.\n" +
                "• Кому подходит: Долгосрочным инвесторам для создания пенсионного капитала.",
                COLOR_PRIMARY
        );
        table.addCell(blueCell);

        // 3. Yellow
        PdfPCell yellowCell = createTrafficCard(
                "🟡 ЖЕЛТЫЙ СЕКТОР (Облигации со скидкой)",
                "Доходность к погашению: 18.0% – 22.0% годовых",
                "• Инструменты: Выпуски надежных банков и корпораций, которые торгуются дешевле 95% от номинала (например, HSBKb22 по 920 ₸).\n" +
                "• Двойная выгода: Вы получаете регулярный купон + гарантированную прибыль при возврате 100% номинала эмитентом.\n" +
                "• Кому подходит: Инвесторам, ищущим повышенную доходность.",
                COLOR_WARNING
        );
        table.addCell(yellowCell);

        // 4. Red
        PdfPCell redCell = createTrafficCard(
                "🔴 КРАСНЫЙ СЕКТОР (Зона риска / Неликвид)",
                "Малоликвидные бумаги без маркетмейкеров",
                "• Инструменты: Акции и облигации третьего эшелона, где сделки проходят раз в полгода.\n" +
                "• Главная опасность: Сложно продать бумаги по справедливой цене, высокий спред стакана.\n" +
                "• Правило для новичка: Не покупать бумаги без аккредитованного маркетмейкера KASE!",
                COLOR_DANGER
        );
        table.addCell(redCell);

        doc.add(table);
    }

    private void renderPage4PaycheckAndHowTo(Document doc, ReportMarketSnapshotDto s) throws Exception {
        Paragraph title = new Paragraph("КАЛЕНДАРЬ КУПОННОЙ ЗАРПЛАТЫ НА 12 МЕСЯЦЕВ", fontProvider.getTitleFont(14f, TEXT_DARK));
        title.setSpacingAfter(4f);
        doc.add(title);

        Paragraph desc = new Paragraph("Готовая корзина из 4-х бумаг со смещенными датами выплат (деньги приходят каждый месяц года)", fontProvider.getBodyFont(9.5f, TEXT_MUTED));
        desc.setSpacingAfter(10f);
        doc.add(desc);

        // 12 Months Grid
        PdfPTable calTable = new PdfPTable(6);
        calTable.setWidthPercentage(100);
        calTable.setSpacingAfter(14f);

        List<ReportMarketSnapshotDto.CouponMonthDto> calendar = s.getPaycheck12Months();
        if (calendar != null) {
            for (ReportMarketSnapshotDto.CouponMonthDto m : calendar) {
                PdfPCell mCell = new PdfPCell();
                mCell.setBackgroundColor(new Color(241, 245, 249));
                mCell.setBorderColor(BORDER_LIGHT);
                mCell.setPadding(6f);

                Paragraph mName = new Paragraph(m.getMonthName() + " (" + m.getMonthNumber() + ")", fontProvider.getBoldFont(9f, COLOR_PRIMARY));
                Paragraph mCode = new Paragraph(m.getIssuerCode(), fontProvider.getBoldFont(8f, TEXT_DARK));
                Paragraph mPay = new Paragraph("+" + String.format("%,d", m.getPayoutAmount().longValue()) + " ₸", fontProvider.getBoldFont(8.5f, COLOR_SUCCESS));

                mCell.addElement(mName);
                mCell.addElement(mCode);
                mCell.addElement(mPay);
                calTable.addCell(mCell);
            }
        }
        doc.add(calTable);

        // USD Shield Section
        Paragraph usdTitle = new Paragraph("🛡️ ДОЛЛАРОВЫЙ ЩИТ: 8.0% В USD ПРОТИВ 1.0% В БАНКЕ", fontProvider.getBoldFont(11f, COLOR_PRIMARY));
        usdTitle.setSpacingAfter(4f);
        doc.add(usdTitle);

        PdfPTable usdTable = new PdfPTable(1);
        usdTable.setWidthPercentage(100);
        usdTable.setSpacingAfter(14f);

        PdfPCell uCell = new PdfPCell();
        uCell.setBackgroundColor(new Color(239, 246, 255));
        uCell.setBorderColor(new Color(191, 219, 254));
        uCell.setPadding(8f);

        Paragraph uText = new Paragraph(
                "Валютный депозит в банках Казахстана дает всего 1.0% годовых в долларах (ограничение КФГД).\n" +
                "На бирже KASE торгуются корпоративные и суверенные облигации в долларах США (USD) с купоном от 6.5% до 8.5% годовых!\n" +
                "Это в 8 раз выгоднее банковского вклада, полностью защищает от девальвации тенге и также освобождено от налога (ИПН 0%).",
                fontProvider.getBodyFont(8.5f, TEXT_DARK)
        );
        uCell.addElement(uText);
        usdTable.addCell(uCell);
        doc.add(usdTable);

        // 3 Simple Steps
        Paragraph howTitle = new Paragraph("КАК НАЧАТЬ ИНВЕСТИРОВАТЬ ЗА 3 ШАГА", fontProvider.getBoldFont(11f, TEXT_DARK));
        howTitle.setSpacingAfter(4f);
        doc.add(howTitle);

        PdfPTable stepTable = new PdfPTable(3);
        stepTable.setWidthPercentage(100);
        addStepCard(stepTable, "Шаг 1: Счет за 2 минуты", "Откройте брокерский счет онлайн прямо в приложении Halyk, Freedom Broker, BCC Trade или Jusan. Это бесплатно.", fontProvider);
        addStepCard(stepTable, "Шаг 2: Пополнение без %", "Пополните брокерский счет с любой карты казахстанского банка без комиссии через мобильный банкинг.", fontProvider);
        addStepCard(stepTable, "Шаг 3: Покупка в 1 клик", "Найдите тикер облигации (например, JSBNb13) и нажмите «Купить». Купоны будут падать на счет автоматически!", fontProvider);
        doc.add(stepTable);
    }

    private void addTipCard(PdfPTable table, String title, String text, Color accentColor) {
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(CARD_BG);
        c.setBorderColor(BORDER_LIGHT);
        c.setPadding(8f);

        Paragraph t = new Paragraph(title, fontProvider.getBoldFont(9f, accentColor));
        t.setSpacingAfter(3f);
        c.addElement(t);

        Paragraph b = new Paragraph(text, fontProvider.getBodyFont(8f, TEXT_MUTED));
        c.addElement(b);
        table.addCell(c);
    }

    private void addSimpleRow(PdfPTable table, String title, String val) {
        PdfPCell c1 = new PdfPCell(new Phrase(title, fontProvider.getBodyFont(9f, TEXT_MUTED)));
        c1.setBackgroundColor(CARD_BG);
        c1.setBorderColor(BORDER_LIGHT);
        c1.setPadding(5f);

        PdfPCell c2 = new PdfPCell(new Phrase(val, fontProvider.getBoldFont(9.5f, COLOR_PRIMARY)));
        c2.setBackgroundColor(CARD_BG);
        c2.setBorderColor(BORDER_LIGHT);
        c2.setPadding(5f);
        c2.setHorizontalAlignment(Element.ALIGN_RIGHT);

        table.addCell(c1);
        table.addCell(c2);
    }

    private PdfPCell createBattleCard(String name, String rate, String income, String points, Color borderColor, Color rateColor) {
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(CARD_BG);
        c.setBorderColor(borderColor);
        c.setBorderWidth(borderColor.equals(COLOR_SUCCESS) ? 1.5f : 1.0f);
        c.setPadding(8f);

        Paragraph pName = new Paragraph(name, fontProvider.getBoldFont(9f, TEXT_DARK));
        Paragraph pRate = new Paragraph(rate, fontProvider.getTitleFont(14f, rateColor));
        Paragraph pInc = new Paragraph(income, fontProvider.getBoldFont(9.5f, rateColor));
        pInc.setSpacingAfter(6f);

        c.addElement(pName);
        c.addElement(pRate);
        c.addElement(pInc);

        Paragraph pts = new Paragraph(points, fontProvider.getBodyFont(7.5f, TEXT_MUTED));
        c.addElement(pts);
        return c;
    }

    private PdfPCell createTrafficCard(String title, String subtitle, String content, Color accent) {
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(CARD_BG);
        c.setBorderColor(BORDER_LIGHT);
        c.setPadding(8f);

        Paragraph t = new Paragraph(title, fontProvider.getBoldFont(9.5f, accent));
        Paragraph sub = new Paragraph(subtitle, fontProvider.getBoldFont(8f, TEXT_DARK));
        sub.setSpacingAfter(4f);
        Paragraph body = new Paragraph(content, fontProvider.getBodyFont(7.5f, TEXT_MUTED));

        c.addElement(t);
        c.addElement(sub);
        c.addElement(body);
        return c;
    }

    private void addStepCard(PdfPTable table, String title, String desc, ReportFontProvider fp) {
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(CARD_BG);
        c.setBorderColor(BORDER_LIGHT);
        c.setPadding(8f);

        Paragraph t = new Paragraph(title, fp.getBoldFont(9f, COLOR_PRIMARY));
        t.setSpacingAfter(3f);
        Paragraph d = new Paragraph(desc, fp.getBodyFont(8f, TEXT_MUTED));

        c.addElement(t);
        c.addElement(d);
        table.addCell(c);
    }
}
