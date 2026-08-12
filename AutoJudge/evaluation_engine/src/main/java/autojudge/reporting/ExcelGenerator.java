package autojudge.reporting;

import autojudge.CoreEvaluation.model.SubmissionResult;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates an Excel (.xlsx) report from a collection of SubmissionResult objects.
 */
public class ExcelGenerator {

    private static final Logger log = LoggerFactory.getLogger(ExcelGenerator.class);

    /**
     * Generates an Excel report file at the given output path.
     *
     * @param results    List of evaluation results to include in the report.
     * @param outputPath Target file path for the .xlsx file.
     * @throws IOException If file creation or writing fails.
     */
    public void generateReport(List<SubmissionResult> results, Path outputPath) throws IOException {
        log.info("Generating Excel report with {} result(s) at {}", results.size(), outputPath);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Evaluation Results");

            // Header Style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Create Header Row
            String[] headers = {"Submission ID", "Student ID", "Assignment ID", "Verdict", "Score (%)", "Passed Tests", "Total Tests"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Populate Data Rows
            int rowIndex = 1;
            for (SubmissionResult result : results) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(result.submissionId() != null ? result.submissionId() : "");
                row.createCell(1).setCellValue(result.studentId() != null ? result.studentId() : "");
                row.createCell(2).setCellValue(result.assignmentId() != null ? result.assignmentId() : "");
                row.createCell(3).setCellValue(result.verdict() != null ? result.verdict().name() : "UNKNOWN");
                row.createCell(4).setCellValue(result.score());
                row.createCell(5).setCellValue(result.passedTests());
                row.createCell(6).setCellValue(result.totalTests());
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Ensure parent directory exists
            if (outputPath.getParent() != null) {
                java.nio.file.Files.createDirectories(outputPath.getParent());
            }

            try (FileOutputStream out = new FileOutputStream(outputPath.toFile())) {
                workbook.write(out);
            }

            log.info("Excel report generated successfully at {}", outputPath);
        }
    }
}
