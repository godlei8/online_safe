package com.godlei.onlinesafe.vaultimport.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportAiMapperJsonExtractTest {

    @Test
    void stripsMarkdownFence() {
        String raw = """
                ```json
                {"items":[{"rowIndex":1}]}
                ```
                """;
        String json = ImportAiMapper.extractJsonObject(raw);
        assertTrue(json.contains("\"items\""));
        assertEquals("{\"items\":[{\"rowIndex\":1}]}", json.replaceAll("\\s+", ""));
    }

    @Test
    void extractsEmbeddedObject() {
        String raw = "好的，结果如下：\n{\"items\":[]}\n请查收";
        assertEquals("{\"items\":[]}", ImportAiMapper.extractJsonObject(raw));
    }
}
