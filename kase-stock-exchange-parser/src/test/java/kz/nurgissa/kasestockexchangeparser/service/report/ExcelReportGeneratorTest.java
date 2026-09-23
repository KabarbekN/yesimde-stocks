package kz.nurgissa.kasestockexchangeparser.service.report;

import kz.nurgissa.kasestockexchangeparser.model.dtos.ReportMarketSnapshotDto;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelReportGeneratorTest {

    private ExcelReportGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new ExcelReportGenerator();
    }

    @Test
    void generateExcelReport_shouldCreateValidFourTabWorkbook() throws IOException {
        ReportMarketSnapshotDto snapshot = ReportTestFixtures.createTestSnapshot();

        byte[] excelBytes = generator.generateExcelReport(snapshot);

        assertThat(excelBytes).isNotNull().isNotEmpty();
        assertThat(excelBytes.length).isGreaterThan(1000);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(4);

            Sheet sheet0 = workbook.getSheetAt(0);
            assertThat(sheet0.getSheetName()).contains("Акции");
            assertThat(sheet0.getRow(1).getCell(0).getStringCellValue()).contains("Тикер");

            Sheet sheet1 = workbook.getSheetAt(1);
            assertThat(sheet1.getSheetName()).contains("Облигации");
            assertThat(sheet1.getRow(1).getCell(0).getStringCellValue()).contains("Тикер");

            Sheet sheet2 = workbook.getSheetAt(2);
            assertThat(sheet2.getSheetName()).contains("Арбитраж");
            assertThat(sheet2.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Эмитент");

            Sheet sheet3 = workbook.getSheetAt(3);
            assertThat(sheet3.getSheetName()).contains("Макро");
        }
    }
}
