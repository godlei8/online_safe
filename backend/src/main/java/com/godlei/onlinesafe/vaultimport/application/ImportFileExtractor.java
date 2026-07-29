package com.godlei.onlinesafe.vaultimport.application;

import com.godlei.onlinesafe.vaultimport.domain.ImportFileFormat;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;

@Component
public class ImportFileExtractor {

    private final CsvImportExtractor csvImportExtractor;
    private final ExcelImportExtractor excelImportExtractor;
    private final MarkdownImportExtractor markdownImportExtractor;
    private final TextImportExtractor textImportExtractor;

    public ImportFileExtractor(
            CsvImportExtractor csvImportExtractor,
            ExcelImportExtractor excelImportExtractor,
            MarkdownImportExtractor markdownImportExtractor,
            TextImportExtractor textImportExtractor
    ) {
        this.csvImportExtractor = csvImportExtractor;
        this.excelImportExtractor = excelImportExtractor;
        this.markdownImportExtractor = markdownImportExtractor;
        this.textImportExtractor = textImportExtractor;
    }

    public ImportFileFormat detectFormat(String fileName) {
        String lower = fileName == null ? "" : fileName.trim().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".xlsx")) {
            return ImportFileFormat.XLSX;
        }
        if (lower.endsWith(".xls")) {
            return ImportFileFormat.XLS;
        }
        if (lower.endsWith(".csv")) {
            return ImportFileFormat.CSV;
        }
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
            return ImportFileFormat.MD;
        }
        if (lower.endsWith(".txt")) {
            return ImportFileFormat.TXT;
        }
        throw new VaultImportException("IMPORT_FILE_TYPE_UNSUPPORTED", "仅支持 xlsx、xls、csv、md、txt 文件");
    }

    public ImportDocument extract(MultipartFile file, int maxRows) {
        String fileName = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new VaultImportException("IMPORT_FILE_TYPE_UNSUPPORTED", "无法读取上传文件");
        }
        return extract(fileName, bytes, maxRows);
    }

    public ImportDocument extract(String fileName, byte[] bytes, int maxRows) {
        ImportFileFormat format = detectFormat(fileName);
        ImportDocument document = switch (format) {
            case CSV -> csvImportExtractor.extract(fileName, bytes);
            case XLSX, XLS -> excelImportExtractor.extract(fileName, format, bytes);
            case MD -> markdownImportExtractor.extract(fileName, bytes);
            case TXT -> textImportExtractor.extract(fileName, bytes);
        };
        if (document.rows().size() > maxRows) {
            throw new VaultImportException(
                    "IMPORT_ROW_LIMIT_EXCEEDED",
                    "单次最多导入 " + maxRows + " 条，请拆分文件后重试"
            );
        }
        return document;
    }
}
