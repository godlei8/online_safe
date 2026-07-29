package com.godlei.onlinesafe.vaultimport.application;

import com.godlei.onlinesafe.vaultimport.domain.ImportFileFormat;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CsvImportExtractor {

    public ImportDocument extract(String fileName, byte[] bytes) {
        String text = decode(bytes).replace("\r\n", "\n").replace('\r', '\n');
        if (text.isBlank()) {
            return new ImportDocument(fileName, ImportFileFormat.CSV, bytes.length, List.of());
        }
        // 先探测分隔符：取首行未进入引号续行时的内容
        int headerEnd = endOfRecord(text, 0);
        String headerLine = text.substring(0, headerEnd);
        char delimiter = detectDelimiter(headerLine);

        List<List<String>> records = parseRecords(text, delimiter);
        if (records.isEmpty()) {
            return new ImportDocument(fileName, ImportFileFormat.CSV, bytes.length, List.of());
        }

        List<String> rawHeaders = records.getFirst();
        List<String> headers = new ArrayList<>(rawHeaders.size());
        for (int c = 0; c < rawHeaders.size(); c++) {
            headers.add(normalizeHeader(rawHeaders.get(c), c));
        }

        List<ImportDocument.RawRow> rows = new ArrayList<>();
        for (int i = 1; i < records.size(); i++) {
            List<String> values = records.get(i);
            Map<String, String> cells = new LinkedHashMap<>();
            for (int c = 0; c < headers.size(); c++) {
                String value = c < values.size() ? values.get(c) : "";
                cells.put(headers.get(c), value == null ? "" : value.trim());
            }
            if (cells.values().stream().allMatch(String::isBlank)) {
                continue;
            }
            // rowIndex 用记录序号（含表头），便于与表格软件行号对齐
            rows.add(new ImportDocument.RawRow(i + 1, List.copyOf(headers), cells, null));
        }
        return new ImportDocument(fileName, ImportFileFormat.CSV, bytes.length, List.copyOf(rows));
    }

    /**
     * 按 RFC4180 解析整份 CSV：引号内的换行属于同一条记录，不会拆成假行。
     */
    static List<List<String>> parseRecords(String text, char delimiter) {
        List<List<String>> records = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldStarted = false;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                    fieldStarted = true;
                } else {
                    inQuotes = !inQuotes;
                    fieldStarted = true;
                }
                continue;
            }
            if (!inQuotes && ch == delimiter) {
                current.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
                continue;
            }
            if (!inQuotes && ch == '\n') {
                current.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
                if (!(current.size() == 1 && current.getFirst().isBlank())) {
                    records.add(List.copyOf(current));
                }
                current = new ArrayList<>();
                continue;
            }
            field.append(ch);
            fieldStarted = true;
        }

        if (fieldStarted || !current.isEmpty() || field.length() > 0) {
            current.add(field.toString());
        }
        if (!current.isEmpty() && !(current.size() == 1 && current.getFirst().isBlank())) {
            records.add(List.copyOf(current));
        }
        return records;
    }

    private static int endOfRecord(String text, int start) {
        boolean inQuotes = false;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }
            if (!inQuotes && ch == '\n') {
                return i;
            }
        }
        return text.length();
    }

    private static String decode(byte[] bytes) {
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (!utf8.contains("\uFFFD")) {
            return utf8;
        }
        return new String(bytes, Charset.forName("GBK"));
    }

    private static char detectDelimiter(String headerLine) {
        int commas = count(headerLine, ',');
        int semis = count(headerLine, ';');
        int tabs = count(headerLine, '\t');
        if (tabs >= commas && tabs >= semis && tabs > 0) {
            return '\t';
        }
        if (semis > commas) {
            return ';';
        }
        return ',';
    }

    private static int count(String text, char ch) {
        int n = 0;
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (!inQuotes && c == ch) {
                n++;
            }
        }
        return n;
    }

    private static String normalizeHeader(String header, int index) {
        String cleaned = header == null ? "" : header.replace("\uFEFF", "").trim();
        return cleaned.isBlank() ? "列" + (index + 1) : cleaned;
    }
}
