package projet.app.service.excel;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import projet.app.dto.ExcelRowDto;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;

/**
 * Simple Excel reader service.
 */
@Slf4j
@Service
public class ExcelReaderService {

    private final DataFormatter dataFormatter = new DataFormatter();

    /**
     * Read all rows from an Excel file.
     */
    public List<ExcelRowDto> readExcelFile(Path filePath, String batchId) {
        log.info("Reading Excel file: {}", filePath);
        List<ExcelRowDto> rows = new ArrayList<>();
        
        try (InputStream inputStream = new FileInputStream(filePath.toFile());
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new RuntimeException("No sheets found in workbook");
            }
            
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new RuntimeException("No header row found");
            }
            
            List<String> headers = readHeaderRow(headerRow);
            log.info("Found {} columns", headers.size());
            
            int totalRows = sheet.getLastRowNum();
            
            for (int rowIndex = 1; rowIndex <= totalRows; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                
                if (row == null || isEmptyRow(row)) {
                    continue;
                }
                
                try {
                    ExcelRowDto dto = readDataRow(row, headers, rowIndex, filePath.getFileName().toString(), batchId);
                    rows.add(dto);
                } catch (Exception e) {
                    log.warn("Error reading row {}: {}", rowIndex, e.getMessage());
                }
                
                if (rowIndex % 1000 == 0) {
                    log.info("Processed {} rows...", rowIndex);
                }
            }
            
            log.info("Read {} rows from file", rows.size());
            return rows;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to read Excel file: " + filePath, e);
        }
    }

    private List<String> readHeaderRow(Row headerRow) {
        List<String> headers = new ArrayList<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            String value = getCellValueAsString(cell);
            headers.add(value != null ? value.trim() : "column_" + i);
        }
        return headers;
    }

    private ExcelRowDto readDataRow(Row row, List<String> headers, int rowIndex, String sourceFile, String batchId) {
        Map<String, String> data = new LinkedHashMap<>();
        StringBuilder rawData = new StringBuilder();
        
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = row.getCell(i);
            String value = getCellValueAsString(cell);
            data.put(headers.get(i), value);
            if (i > 0) rawData.append("|");
            rawData.append(value != null ? value : "");
        }
        
        return ExcelRowDto.builder()
                .rowNumber(rowIndex)
                .sourceFile(sourceFile)
                .batchId(batchId)
                .data(data)
                .valid(true)
                .rawRowData(rawData.toString())
                .build();
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return null;
        
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield dataFormatter.formatCellValue(cell);
                }
                // Check if the numeric value is a whole number (integer)
                double numericValue = cell.getNumericCellValue();
                if (numericValue == Math.floor(numericValue) && !Double.isInfinite(numericValue)) {
                    // It's a whole number, format as integer (no decimal)
                    yield String.valueOf((long) numericValue);
                }
                yield String.valueOf(numericValue);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> dataFormatter.formatCellValue(cell);
            default -> null;
        };
    }

    private boolean isEmptyRow(Row row) {
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String value = getCellValueAsString(cell);
                if (value != null && !value.trim().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }
}
