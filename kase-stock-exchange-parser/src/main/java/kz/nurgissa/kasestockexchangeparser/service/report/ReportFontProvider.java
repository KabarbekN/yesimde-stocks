package kz.nurgissa.kasestockexchangeparser.service.report;

import com.lowagie.text.Font;
import com.lowagie.text.pdf.BaseFont;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Slf4j
@Component
public class ReportFontProvider {

    private BaseFont regularBaseFont;
    private BaseFont boldBaseFont;

    public ReportFontProvider() {
        initFonts();
    }

    private void initFonts() {
        try {
            // Try loading from classpath resources
            ClassPathResource regRes = new ClassPathResource("fonts/arial.ttf");
            ClassPathResource boldRes = new ClassPathResource("fonts/arialbd.ttf");

            if (regRes.exists()) {
                Path tempReg = Files.createTempFile("kase_arial_", ".ttf");
                tempReg.toFile().deleteOnExit();
                try (InputStream is = regRes.getInputStream()) {
                    Files.copy(is, tempReg, StandardCopyOption.REPLACE_EXISTING);
                }
                regularBaseFont = BaseFont.createFont(tempReg.toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            }

            if (boldRes.exists()) {
                Path tempBold = Files.createTempFile("kase_arialbd_", ".ttf");
                tempBold.toFile().deleteOnExit();
                try (InputStream is = boldRes.getInputStream()) {
                    Files.copy(is, tempBold, StandardCopyOption.REPLACE_EXISTING);
                }
                boldBaseFont = BaseFont.createFont(tempBold.toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            }
        } catch (Exception e) {
            log.warn("Could not load bundled TrueType fonts, falling back to system fonts: {}", e.getMessage());
        }

        // Fallbacks
        if (regularBaseFont == null) {
            try {
                if (Files.exists(Path.of("C:/Windows/Fonts/arial.ttf"))) {
                    regularBaseFont = BaseFont.createFont("C:/Windows/Fonts/arial.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                } else {
                    regularBaseFont = BaseFont.createFont(BaseFont.HELVETICA, "Cp1251", BaseFont.NOT_EMBEDDED);
                }
            } catch (Exception ex) {
                log.error("Fatal: failed to create base font: {}", ex.getMessage());
            }
        }

        if (boldBaseFont == null) {
            try {
                if (Files.exists(Path.of("C:/Windows/Fonts/arialbd.ttf"))) {
                    boldBaseFont = BaseFont.createFont("C:/Windows/Fonts/arialbd.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                } else {
                    boldBaseFont = regularBaseFont;
                }
            } catch (Exception ex) {
                boldBaseFont = regularBaseFont;
            }
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
