package com.godlei.onlinesafe.vaultimport.application;

import com.godlei.onlinesafe.vaultimport.domain.ImportFileFormat;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ExcelImportExtractor {

    private final DataFormatter formatter = new DataFormatter();

    public ImportDocument extract(String fileName, ImportFileFormat format, byte[] bytes) {
        try (Workbook workbook = open(format, bytes)) {
            Sheet sheet = firstNonEmptySheet(workbook);
            if (sheet == null) {
                return new ImportDocument(fileName, format, bytes.length, List.of());
            }
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                return new ImportDocument(fileName, format, bytes.length, List.of());
            }
            List<String> headers = readRow(headerRow);
            if (headers.stream().allMatch(String::isBlank)) {
                return new ImportDocument(fileName, format, bytes.length, List.of());
            }
            List<ImportDocument.RawRow> rows = new ArrayList<>();
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                List<String> values = readRow(row);
                Map<String, String> cells = new LinkedHashMap<>();
                for (int c = 0; c < headers.size(); c++) {
                    String header = headers.get(c).isBlank() ? "列" + (c + 1) : headers.get(c);
                    String value = c < values.size() ? values.get(c) : "";
                    cells.put(header, value);
                }
                if (cells.values().stream().allMatch(String::isBlank)) {
                    continue;
                }
                rows.add(new ImportDocument.RawRow(r + 1, List.copyOf(headers), cells, null));
            }
            return new ImportDocument(fileName, format, bytes.length, List.copyOf(rows));
        } catch (IOException exception) {
            throw new VaultImportException("IMPORT_FILE_TYPE_UNSUPPORTED", "无法解析 Excel 文件");
        }
    }

    private Workbook open(ImportFileFormat format, byte[] bytes) throws IOException {
        ByteArrayInputStream input = new ByteArrayInputStream(bytes);
        if (format == ImportFileFormat.XLS) {
            return new HSSFWorkbook(input);
        }
        return new XSSFWorkbook(input);
    }

    private Sheet firstNonEmptySheet(Workbook workbook) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            if (sheet != null && sheet.getPhysicalNumberOfRows() > 0) {
                return sheet;
            }
        }
        return null;
    }

    private List<String> readRow(Row row) {
        short last = row.getLastCellNum();
        if (last < 0) {
            return List.of();
        }
        List<String> values = new ArrayList<>(last);
        for (int i = 0; i < last; i++) {
            Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            values.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
        }
        return values;
    }
}
