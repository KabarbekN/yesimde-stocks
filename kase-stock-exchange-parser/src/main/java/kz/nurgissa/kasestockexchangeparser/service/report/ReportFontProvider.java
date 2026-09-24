package kz.nurgissa.kasestockexchangeparser.service.report;

import com.lowagie.text.Font;
import com.lowagie.text.pdf.BaseFont;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.InputStream;

@Slf4j
@Component
public class ReportFontProvider {

    private BaseFont regularBaseFont;
    private BaseFont boldBaseFont;

    public ReportFontProvider() {
        initFonts();
    }

    private void initFonts() {
        // Load from classpath using byte arrays — no temp files, works in Docker
        regularBaseFont = loadFontFromClasspath("fonts/arial.ttf");
        boldBaseFont = loadFontFromClasspath("fonts/arialbd.ttf");

        // Fallback: DejaVu Sans is installed via apk in Dockerfile and supports Cyrillic/Kazakh
        if (regularBaseFont == null) {
            regularBaseFont = loadFontFromPath("/usr/share/fonts/ttf-dejavu/DejaVuSans.ttf");
        }
        if (boldBaseFont == null) {
            boldBaseFont = loadFontFromPath("/usr/share/fonts/ttf-dejavu/DejaVuSans-Bold.ttf");
        }

        // Final fallback: Helvetica (limited charset — no Kazakh chars)
        if (regularBaseFont == null) {
            try {
                regularBaseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
                log.warn("Using Helvetica fallback — Kazakh characters (Ә, Ғ, Қ, ₸) will not render");
            } catch (Exception ex) {
                log.error("Fatal: failed to create any base font: {}", ex.getMessage());
            }
        }

        if (boldBaseFont == null) {
            boldBaseFont = regularBaseFont;
        }
    }

    private BaseFont loadFontFromClasspath(String path) {
        try {
            ClassPathResource res = new ClassPathResource(path);
            if (!res.exists()) return null;
            try (InputStream is = res.getInputStream()) {
                byte[] fontBytes = is.readAllBytes();
                return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, fontBytes, null);
            }
        } catch (Exception e) {
            log.warn("Could not load font from classpath '{}': {}", path, e.getMessage());
            return null;
        }
    }

    private BaseFont loadFontFromPath(String absolutePath) {
        try {
            java.io.File f = new java.io.File(absolutePath);
            if (!f.exists()) return null;
            return BaseFont.createFont(absolutePath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        } catch (Exception e) {
            log.warn("Could not load font from path '{}': {}", absolutePath, e.getMessage());
            return null;
        }
    }

    public Font getFont(float size, int style, Color color) {
        BaseFont bf = (style == Font.BOLD) ? boldBaseFont : regularBaseFont;
        return new Font(bf, size, style, color);
    }

    public Font getTitleFont(float size, Color color) {
        return getFont(size, Font.BOLD, color);
    }

    public Font getBodyFont(float size, Color color) {
        return getFont(size, Font.NORMAL, color);
    }

    public Font getBoldFont(float size, Color color) {
        return getFont(size, Font.BOLD, color);
    }
}
