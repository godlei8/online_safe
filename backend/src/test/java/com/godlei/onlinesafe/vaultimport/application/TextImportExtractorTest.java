package com.godlei.onlinesafe.vaultimport.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextImportExtractorTest {

    private final TextImportExtractor extractor = new TextImportExtractor();

    @Test
    void parsesKeyValueBlocks() {
        byte[] bytes = """
                名称：测试账号
                平台：示例
                账号：demo
                密码：123456

                名称: 另一条
                平台: 其他
                """.getBytes(StandardCharsets.UTF_8);

        ImportDocument document = extractor.extract("notes.txt", bytes);

        assertEquals(2, document.rows().size());
        assertEquals("测试账号", document.rows().getFirst().cells().get("名称"));
        assertEquals("另一条", document.rows().get(1).cells().get("名称"));
    }

    @Test
    void infersNameFromTitleLineWithoutNameKey() {
        byte[] bytes = """
                Notion 协作空间
                邮箱: notion.workspace@demo-lab.io
                口令: Notion#Space99
                网址: https://www.notion.so
                """.getBytes(StandardCharsets.UTF_8);

        ImportDocument document = extractor.extract("notion.txt", bytes);

        assertEquals(1, document.rows().size());
        assertEquals("Notion 协作空间", document.rows().getFirst().cells().get("名称"));
        assertEquals("notion.workspace@demo-lab.io", document.rows().getFirst().cells().get("邮箱"));
        assertEquals("https://www.notion.so", document.rows().getFirst().cells().get("网址"));
    }
}
