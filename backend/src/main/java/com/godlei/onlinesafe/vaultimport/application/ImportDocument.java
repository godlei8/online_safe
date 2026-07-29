package com.godlei.onlinesafe.vaultimport.application;

import com.godlei.onlinesafe.vaultimport.domain.ImportFileFormat;

import java.util.List;
import java.util.Map;

public record ImportDocument(
        String sourceFileName,
        ImportFileFormat format,
        long byteSize,
        List<RawRow> rows
) {
    public record RawRow(
            int rowIndex,
            List<String> headers,
            Map<String, String> cells,
            String rawText
    ) {
    }
}
