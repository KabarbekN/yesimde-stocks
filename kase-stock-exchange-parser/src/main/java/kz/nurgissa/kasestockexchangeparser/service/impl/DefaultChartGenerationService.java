package kz.nurgissa.kasestockexchangeparser.service.impl;

import kz.nurgissa.kasestockexchangeparser.model.dtos.ReportMarketSnapshotDto;
import kz.nurgissa.kasestockexchangeparser.service.ChartGenerationService;
import lombok.extern.slf4j.Slf4j;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@Service
public class DefaultChartGenerationService implements ChartGenerationService {

    @Override
    public byte[] generateTopEquitiesBarChart(List<ReportMarketSnapshotDto.StockReportItemDto> topStocks, boolean darkMode, int width, int height) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();

        if (topStocks != null) {
            int count = Math.min(topStocks.size(), 8);
            for (int i = 0; i < count; i++) {
                ReportMarketSnapshotDto.StockReportItemDto s = topStocks.get(i);
                BigDecimal volBln = s.getCumulativeVolumeKzt() != null
                        ? s.getCumulativeVolumeKzt().divide(BigDecimal.valueOf(1_000_000_000L), 1, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                dataset.addValue(volBln.doubleValue(), "Оборот (млрд ₸)", s.getCode());
            }
        }

        JFreeChart chart = ChartFactory.createBarChart(
                "Топ-8 акций по биржевому обороту (млрд ₸, 2022–2026)",
                "Тикер",
                "Млрд тенге",
                dataset,
                PlotOrientation.VERTICAL,
                false,
                false,
                false
        );

        Color bgColor = Color.WHITE;
        Color fgColor = new Color(30, 41, 59);
        Color barColor = new Color(37, 99, 235);

        chart.setBackgroundPaint(bgColor);
        chart.getTitle().setPaint(fgColor);
        chart.getTitle().setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));

        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(new Color(248, 250, 252));
        plot.setOutlinePaint(new Color(226, 232, 240));
        plot.setRangeGridlinePaint(new Color(226, 232, 240));

        CategoryAxis domainAxis = plot.getDomainAxis();
        domainAxis.setTickLabelPaint(fgColor);
        domainAxis.setLabelPaint(fgColor);
        domainAxis.setTickLabelFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        domainAxis.setCategoryLabelPositions(org.jfree.chart.axis.CategoryLabelPositions.UP_45);

        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setTickLabelPaint(fgColor);
        rangeAxis.setLabelPaint(fgColor);
        rangeAxis.setTickLabelFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));

        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        renderer.setBarPainter(new StandardBarPainter());
        renderer.setSeriesPaint(0, barColor);
        renderer.setShadowVisible(false);

        return renderChartToPng(chart, width, height);
    }

    @Override
    public byte[] generateYieldCurveChart(boolean darkMode, int width, int height) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(16.9, "ГЦБ Минфина (Суверенная)", "3 мес.");
        dataset.addValue(16.5, "ГЦБ Минфина (Суверенная)", "6 мес.");
        dataset.addValue(15.8, "ГЦБ Минфина (Суверенная)", "1 год");
        dataset.addValue(15.2, "ГЦБ Минфина (Суверенная)", "2 года");
        dataset.addValue(14.8, "ГЦБ Минфина (Суверенная)", "3 года");
        dataset.addValue(14.5, "ГЦБ Минфина (Суверенная)", "5 лет");
        dataset.addValue(14.2, "ГЦБ Минфина (Суверенная)", "10 лет");

        dataset.addValue(17.8, "Квазигоссектор (Отбасы/БРК)", "3 мес.");
        dataset.addValue(17.5, "Квазигоссектор (Отбасы/БРК)", "1 год");
        dataset.addValue(17.45, "Квазигоссектор (Отбасы/БРК)", "3 года");
        dataset.addValue(17.0, "Квазигоссектор (Отбасы/БРК)", "5 лет");

        JFreeChart chart = ChartFactory.createLineChart(
                "Суверенная кривая доходности РК vs Квазигоссектор (% годовых)",
                "Срок обращения (дюрация)",
                "Доходность (% годовых)",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                false,
                false
        );

        Color bgColor = Color.WHITE;
        Color fgColor = new Color(30, 41, 59);

        chart.setBackgroundPaint(bgColor);
        chart.getTitle().setPaint(fgColor);
        chart.getTitle().setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        if (chart.getLegend() != null) {
            chart.getLegend().setBackgroundPaint(bgColor);
            chart.getLegend().setItemPaint(fgColor);
            chart.getLegend().setItemFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        }

        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(new Color(248, 250, 252));
        plot.setOutlinePaint(new Color(226, 232, 240));
        plot.setRangeGridlinePaint(new Color(226, 232, 240));

        plot.getDomainAxis().setTickLabelPaint(fgColor);
        plot.getDomainAxis().setLabelPaint(fgColor);
        plot.getRangeAxis().setTickLabelPaint(fgColor);
        plot.getRangeAxis().setLabelPaint(fgColor);

        plot.getRenderer().setSeriesPaint(0, new Color(37, 99, 235)); // Royal Blue
        plot.getRenderer().setSeriesPaint(1, new Color(16, 185, 129)); // Emerald Green

        return renderChartToPng(chart, width, height);
    }

    @Override
    public byte[] generateSectorPieChart(List<ReportMarketSnapshotDto.StockReportItemDto> stocks, boolean darkMode, int width, int height) {
        DefaultPieDataset dataset = new DefaultPieDataset();
        dataset.setValue("Нефть и Газ (KMGZ)", 414.0);
        dataset.setValue("Банки & FinTech (HSBK, CCBN, KSPI)", 324.6);
        dataset.setValue("Телекоммуникации (KZTK, KCEL)", 246.3);
        dataset.setValue("Авиация (AIRA)", 98.9);
        dataset.setValue("Атомная отрасль (KZAP)", 83.0);
        dataset.setValue("KASE Global (BITO, NVDA, INTC)", 162.4);

        JFreeChart chart = ChartFactory.createPieChart(
                "Отраслевая структура биржевого капитала (млрд ₸)",
                dataset,
                true,
                false,
                false
        );

        Color bgColor = Color.WHITE;
        Color fgColor = new Color(30, 41, 59);

        chart.setBackgroundPaint(bgColor);
        chart.getTitle().setPaint(fgColor);
        chart.getTitle().setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        if (chart.getLegend() != null) {
            chart.getLegend().setBackgroundPaint(bgColor);
            chart.getLegend().setItemPaint(fgColor);
            chart.getLegend().setItemFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
        }

        org.jfree.chart.plot.PiePlot plot = (org.jfree.chart.plot.PiePlot) chart.getPlot();
        plot.setBackgroundPaint(new Color(248, 250, 252));
        plot.setOutlinePaint(new Color(226, 232, 240));
        plot.setLabelBackgroundPaint(bgColor);
        plot.setLabelPaint(fgColor);
        plot.setLabelFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));

        plot.setSectionPaint("Нефть и Газ (KMGZ)", new Color(2, 132, 199));
        plot.setSectionPaint("Банки & FinTech (HSBK, CCBN, KSPI)", new Color(16, 185, 129));
        plot.setSectionPaint("Телекоммуникации (KZTK, KCEL)", new Color(139, 92, 246));
        plot.setSectionPaint("Авиация (AIRA)", new Color(14, 165, 233));
        plot.setSectionPaint("Атомная отрасль (KZAP)", new Color(245, 158, 11));
        plot.setSectionPaint("KASE Global (BITO, NVDA, INTC)", new Color(236, 72, 153));

        return renderChartToPng(chart, width, height);
    }

    @Override
    public byte[] generateFearAndGreedDial(int score, String label, boolean darkMode, int width, int height) {
        int scale = 2; // High-DPI Retina
        BufferedImage image = new BufferedImage(width * scale, height * scale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.scale(scale, scale);

        Color bgColor = Color.WHITE;
        g2.setColor(bgColor);
        g2.fillRect(0, 0, width, height);

        // TRUE CIRCLE GEOMETRY (arcW == arcH guarantees zero vertical squishing!)
        int strokeWidth = 14;
        // Diameter fits width and height (semi-circle requires height >= diameter / 2 + padding)
        int diameter = Math.min(width - strokeWidth * 2 - 24, (height - 30) * 2);
        int arcW = diameter;
        int arcH = diameter;
        int arcX = (width - diameter) / 2;
        int arcY = 10;

        g2.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Background track (Light Slate)
        g2.setColor(new Color(226, 232, 240));
        g2.draw(new Arc2D.Double(arcX, arcY, arcW, arcH, 0, 180, Arc2D.OPEN));

        // Active Arc
        int clampedScore = Math.max(0, Math.min(100, score));
        double angle = 180.0 * (clampedScore / 100.0);
        Color activeColor;
        if (clampedScore >= 75) {
            activeColor = new Color(5, 150, 105); // Emerald 600
        } else if (clampedScore >= 55) {
            activeColor = new Color(16, 185, 129); // Emerald 500
        } else if (clampedScore >= 45) {
            activeColor = new Color(245, 158, 11); // Amber 500
        } else if (clampedScore >= 25) {
            activeColor = new Color(249, 115, 22); // Orange 500
        } else {
            activeColor = new Color(239, 68, 68); // Red 500
        }

        g2.setColor(activeColor);
        // Java Arc2D: startAngle = 180 - angle, extent = angle (counter-clockwise from West)
        g2.draw(new Arc2D.Double(arcX, arcY, arcW, arcH, 180 - angle, angle, Arc2D.OPEN));

        int centerX = width / 2;
        int centerY = arcY + diameter / 2;

        // Big Score text in the center
        g2.setColor(new Color(15, 23, 42));
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
        String scoreStr = String.valueOf(score);
        FontMetrics fm = g2.getFontMetrics();
        int scoreWidth = fm.stringWidth(scoreStr);
        g2.drawString(scoreStr, centerX - scoreWidth / 2, centerY - 6);

        // Label text below score
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        g2.setColor(activeColor);
        fm = g2.getFontMetrics();
        int labelWidth = fm.stringWidth(label);
        g2.drawString(label, centerX - labelWidth / 2, centerY + 10);

        // Scale bounds (0 and 100)
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 8));
        g2.setColor(new Color(148, 163, 184));
        g2.drawString("0", arcX - 4, centerY + 14);
        g2.drawString("100", arcX + diameter - 10, centerY + 14);

        g2.dispose();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error writing dial image: {}", e.getMessage());
            return new byte[0];
        }
    }

    private byte[] renderChartToPng(JFreeChart chart, int width, int height) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            chart.setAntiAlias(true);
            chart.setTextAntiAlias(true);
            int scale = 2; // High-DPI Retina
            BufferedImage image = new BufferedImage(width * scale, height * scale, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.scale(scale, scale);
            chart.draw(g2, new Rectangle(0, 0, width, height));
            g2.dispose();
            ImageIO.write(image, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to render chart to PNG: {}", e.getMessage(), e);
            return new byte[0];
        }
    }
}
