package com.godlei.onlinesafe.vaultimport.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvImportExtractorTest {

    private final CsvImportExtractor extractor = new CsvImportExtractor();

    @Test
    void extractsHeaderAndRows() {
        byte[] bytes = """
                名称,平台,账号,密码
                网易邮箱,网易,user@163.com,secret
                ,,,
                阿里云,阿里,admin,pass
                """.getBytes(StandardCharsets.UTF_8);

        ImportDocument document = extractor.extract("demo.csv", bytes);

        assertEquals(2, document.rows().size());
        assertEquals("网易邮箱", document.rows().getFirst().cells().get("名称"));
        assertEquals("admin", document.rows().get(1).cells().get("账号"));
    }

    @Test
    void detectsSemicolonDelimiter() {
        byte[] bytes = "名称;平台\n测试;平台A\n".getBytes(StandardCharsets.UTF_8);
        ImportDocument document = extractor.extract("demo.csv", bytes);
        assertEquals(1, document.rows().size());
        assertEquals("平台A", document.rows().getFirst().cells().get("平台"));
    }

    @Test
    void keepsQuotedMultilineFieldInOneRow() {
        String csv = "名称,平台,账号,密码,备注\n"
                + "仅备注待核对,未知站点,,,\"账号：orphan.user@demo.test\n"
                + "密码：请人工填入\n"
                + "这段故意放备注里测快速映射补抽\"\n"
                + "公司邮箱,网易,alice@demo.com,secret,正常行\n";
        ImportDocument document = extractor.extract("multiline.csv", csv.getBytes(StandardCharsets.UTF_8));

        assertEquals(2, document.rows().size());
        assertEquals("仅备注待核对", document.rows().getFirst().cells().get("名称"));
        assertEquals("未知站点", document.rows().getFirst().cells().get("平台"));
        assertTrue(document.rows().getFirst().cells().get("备注").contains("密码：请人工填入"));
        assertEquals("公司邮箱", document.rows().get(1).cells().get("名称"));
    }
}
