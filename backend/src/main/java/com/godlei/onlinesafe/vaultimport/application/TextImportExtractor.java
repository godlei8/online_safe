package com.godlei.onlinesafe.vaultimport.application;

import com.godlei.onlinesafe.vaultimport.domain.ImportFileFormat;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TextImportExtractor {

    private static final Pattern KV = Pattern.compile("^([^:：\\n]{1,40})\\s*[：:]\\s*(.*)$");

    public ImportDocument extract(String fileName, byte[] bytes) {
        String text = decode(bytes).replace("\r\n", "\n").replace('\r', '\n').trim();
        String[] blocks = text.split("\\n\\s*\\n+");
        List<ImportDocument.RawRow> rows = new ArrayList<>();
        int index = 1;
        for (String block : blocks) {
            String trimmed = block.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Map<String, String> cells = new LinkedHashMap<>();
            String[] lines = trimmed.split("\n");
            int kvCount = 0;
            String inferredName = null;
            for (String line : lines) {
                String cleaned = line.trim();
                if (cleaned.isEmpty()) {
                    continue;
                }
                Matcher matcher = KV.matcher(cleaned);
                if (matcher.matches()) {
                    cells.put(matcher.group(1).trim(), matcher.group(2).trim());
                    kvCount++;
                } else if (inferredName == null) {
                    inferredName = cleaned;
                }
            }
            if (inferredName != null) {
                cells.putIfAbsent("名称", inferredName);
            }
            if (kvCount == 0) {
                cells.put("原文", trimmed);
            }
            rows.add(new ImportDocument.RawRow(index++, List.copyOf(cells.keySet()), cells, trimmed));
        }
        return new ImportDocument(fileName, ImportFileFormat.TXT, bytes.length, List.copyOf(rows));
    }

    private static String decode(byte[] bytes) {
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (!utf8.contains("\uFFFD")) {
            return utf8;
        }
        return new String(bytes, Charset.forName("GBK"));
    }
}
