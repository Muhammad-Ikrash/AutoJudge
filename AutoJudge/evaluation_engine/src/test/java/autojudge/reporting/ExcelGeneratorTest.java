package autojudge.reporting;

import autojudge.CoreEvaluation.model.SubmissionResult;
import autojudge.CoreEvaluation.model.Verdict;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcelGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    void testGeneratesExcelReportSuccessfully() throws Exception {
        ExcelGenerator generator = new ExcelGenerator();
        Path excelPath = tempDir.resolve("results.xlsx");

        SubmissionResult result1 = new SubmissionResult("sub-1", "assign-1", "student-1", 100.0, Verdict.ACCEPTED, 2, 2, List.of());
        SubmissionResult result2 = new SubmissionResult("sub-2", "assign-1", "student-2", 50.0, Verdict.WRONG_ANSWER, 1, 2, List.of());

        generator.generateReport(List.of(result1, result2), excelPath);

        assertTrue(excelPath.toFile().exists());

        try (Workbook workbook = new XSSFWorkbook(new FileInputStream(excelPath.toFile()))) {
            var sheet = workbook.getSheet("Evaluation Results");
            assertEquals(3, sheet.getPhysicalNumberOfRows()); // Header + 2 data rows
            assertEquals("sub-1", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("student-1", sheet.getRow(1).getCell(1).getStringCellValue());
            assertEquals("ACCEPTED", sheet.getRow(1).getCell(3).getStringCellValue());
            assertEquals(100.0, sheet.getRow(1).getCell(4).getNumericCellValue());
        }
    }
}
