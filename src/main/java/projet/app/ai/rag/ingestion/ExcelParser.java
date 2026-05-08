package projet.app.ai.rag.ingestion;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses an uploaded Excel workbook into {@link RawRow}s, one per data row across
 * all sheets. The first non-empty row of each sheet is treated as the header.
 */
@Slf4j
@Component
public class ExcelParser {

    public List<RawRow> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return List.of();
        }
        List<RawRow> rows = new ArrayList<>();
        try (InputStream in = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(in)) {

            DataFormatter formatter = new DataFormatter();
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                if (sheet == null) continue;
                List<String> headers = readHeaders(sheet, formatter);
                if (headers.isEmpty()) {
                    log.debug("Sheet '{}' has no header row, skipping.", sheet.getSheetName());
                    continue;
                }
                int firstDataRow = sheet.getFirstRowNum() + 1;
                int lastRow = sheet.getLastRowNum();
                for (int r = firstDataRow; r <= lastRow; r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;
                    Map<String, String> cells = new LinkedHashMap<>();
                    boolean anyValue = false;
                    for (int c = 0; c < headers.size(); c++) {
                        Cell cell = row.getCell(c);
                        String value = cell == null ? "" : formatter.formatCellValue(cell).trim();
                        cells.put(headers.get(c), value);
                        if (!value.isEmpty()) anyValue = true;
                    }
                    if (anyValue) {
                        rows.add(new RawRow(r, sheet.getSheetName(), cells));
                    }
                }
            }
        } catch (IOException ex) {
            throw new IngestionException("Failed to read Excel file: " + ex.getMessage(), ex);
        }
        log.info("ExcelParser: parsed {} non-empty rows from '{}'",
                rows.size(), file.getOriginalFilename());
        return rows;
    }

    private List<String> readHeaders(Sheet sheet, DataFormatter formatter) {
        Row headerRow = sheet.getRow(sheet.getFirstRowNum());
        if (headerRow == null) {
            return List.of();
        }
        List<String> headers = new ArrayList<>();
        for (int c = 0; c < headerRow.getLastCellNum(); c++) {
            Cell cell = headerRow.getCell(c);
            String value = cell == null ? "" : formatter.formatCellValue(cell).trim();
            headers.add(value.isEmpty() ? "col_" + c : value);
        }
        return headers;
    }

    /** Fatal ingestion error: malformed file, IO failure, etc. */
    public static class IngestionException extends RuntimeException {
        public IngestionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
