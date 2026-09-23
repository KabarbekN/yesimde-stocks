package kz.nurgissa.kasestockexchangeparser.service.report;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;

import java.awt.Color;

public class ReportPageEventHelper extends PdfPageEventHelper {

    private final ReportFontProvider fontProvider;
    private final boolean darkMode;
    private final String reportTitle;
    private PdfTemplate totalPagesTemplate;

    public ReportPageEventHelper(ReportFontProvider fontProvider, boolean darkMode, String reportTitle) {
        this.fontProvider = fontProvider;
        this.darkMode = darkMode;
        this.reportTitle = reportTitle;
    }

    @Override
    public void onOpenDocument(PdfWriter writer, Document document) {
        totalPagesTemplate = writer.getDirectContent().createTemplate(30, 16);
    }

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        PdfContentByte cb = writer.getDirectContent();

        Color fgColor = darkMode ? new Color(148, 163, 184) : new Color(100, 116, 139);
        Color lineColor = darkMode ? new Color(30, 41, 59) : new Color(226, 232, 240);
        Font font = fontProvider.getFont(8f, Font.NORMAL, fgColor);

        // Header (Skip on Page 1)
        if (writer.getPageNumber() > 1) {
            Phrase headerPhrase = new Phrase(reportTitle + "  •  KASE & AIX", font);
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, headerPhrase, document.left(), document.top() + 12, 0);

            // Header line
            cb.setColorStroke(lineColor);
            cb.setLineWidth(0.5f);
            cb.moveTo(document.left(), document.top() + 8);
            cb.lineTo(document.right(), document.top() + 8);
            cb.stroke();
        }

        // Footer line
        cb.setColorStroke(lineColor);
        cb.setLineWidth(0.5f);
        cb.moveTo(document.left(), document.bottom() - 10);
        cb.lineTo(document.right(), document.bottom() - 10);
        cb.stroke();

        // Footer text
        Phrase footerLeft = new Phrase("KASE & AIX RADAR  |  Официальный биржевой протокол и аналитика", font);
        ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, footerLeft, document.left(), document.bottom() - 20, 0);

        String pageText = "Стр. " + writer.getPageNumber() + " из ";
        float len = fontProvider.getFont(8f, Font.NORMAL, fgColor).getCalculatedBaseFont(true).getWidthPoint(pageText, 8f);
        ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, new Phrase(pageText, font), document.right() - 16, document.bottom() - 20, 0);
        cb.addTemplate(totalPagesTemplate, document.right() - 16, document.bottom() - 23);
    }

    @Override
    public void onCloseDocument(PdfWriter writer, Document document) {
        Font font = fontProvider.getFont(8f, Font.NORMAL, darkMode ? new Color(148, 163, 184) : new Color(100, 116, 139));
        ColumnText.showTextAligned(
                totalPagesTemplate,
                Element.ALIGN_LEFT,
                new Phrase(String.valueOf(writer.getPageNumber() - 1), font),
                2, 3, 0
        );
    }
}
