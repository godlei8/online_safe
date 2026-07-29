package com.godlei.onlinesafe.vaultimport.application;

import com.godlei.onlinesafe.vaultimport.domain.ImportFileFormat;
import com.godlei.onlinesafe.vaultimport.domain.ImportMode;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ImportAiMapperFastTest {

    private final ImportAiMapper mapper = new ImportAiMapper(
            mock(OpenAiCompatibleChatClient.class),
            JsonMapper.builder().build()
    );

    @Test
    void fastModeExtractsAccountPasswordFromFreeformMarkdownRow() {
        Map<String, String> cells = new LinkedHashMap<>();
        cells.put("原文", """
                谷歌账号 (用于Claude Code)
                账号：user@gmail.com
                密码：secret
                辅助邮箱：backup@example.com
                """.trim());
        ImportDocument document = new ImportDocument(
                "notes.md",
                ImportFileFormat.MD,
                10,
                List.of(new ImportDocument.RawRow(1, List.copyOf(cells.keySet()), cells, cells.get("原文")))
        );

        ImportAiMapper.MapResult result = mapper.map(document, ImportMode.FAST, null);

        assertEquals(1, result.candidates().size());
        assertFalse(result.degraded());
        var payload = result.candidates().getFirst().getPayload();
        assertTrue(payload.path("name").asString().contains("谷歌"));
        assertEquals("谷歌", payload.path("platform").asString());
        String account = "";
        String password = "";
        for (var field : payload.path("fields")) {
            if ("account".equals(field.path("systemKey").asString())) {
                account = field.path("value").asString();
            }
            if ("password".equals(field.path("systemKey").asString())) {
                password = field.path("value").asString();
            }
        }
        assertEquals("user@gmail.com", account);
        assertEquals("secret", password);
    }

    @Test
    void fastModeInfersOptionalPlatformButAccountPasswordMatter() {
        Map<String, String> cells = new LinkedHashMap<>();
        cells.put("名称", "Notion 协作空间");
        cells.put("邮箱", "notion.workspace@demo-lab.io");
        cells.put("口令", "Notion#Space99");
        cells.put("网址", "https://www.notion.so");
        String raw = """
                Notion 协作空间
                邮箱: notion.workspace@demo-lab.io
                口令: Notion#Space99
                网址: https://www.notion.so
                """.trim();
        ImportDocument document = new ImportDocument(
                "04-文本块导入测试.txt",
                ImportFileFormat.TXT,
                raw.length(),
                List.of(new ImportDocument.RawRow(1, List.copyOf(cells.keySet()), cells, raw))
        );

        ImportAiMapper.MapResult result = mapper.map(document, ImportMode.FAST, null);
        var payload = result.candidates().getFirst().getPayload();
        assertEquals("Notion", payload.path("platform").asString());
        assertEquals("Notion 协作空间", payload.path("name").asString());
        String account = "";
        String password = "";
        for (var field : payload.path("fields")) {
            if ("account".equals(field.path("systemKey").asString())) {
                account = field.path("value").asString();
            }
            if ("password".equals(field.path("systemKey").asString())) {
                password = field.path("value").asString();
            }
        }
        assertEquals("notion.workspace@demo-lab.io", account);
        assertEquals("Notion#Space99", password);
    }
}
