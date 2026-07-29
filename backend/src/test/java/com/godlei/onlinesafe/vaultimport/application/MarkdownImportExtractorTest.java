package com.godlei.onlinesafe.vaultimport.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownImportExtractorTest {

    private final MarkdownImportExtractor extractor = new MarkdownImportExtractor();

    @Test
    void parsesOnlineSafeExportShape() {
        byte[] bytes = """
                ## 私人邮箱
                - 平台：网易
                - 渠道：个人
                - 渠道网址：—
                - 状态：正常
                - 有效期：永久有效

                ### 字段

                | 字段 | 值 |
                | --- | --- |
                | 账号 | user@163.com |
                | 密码 | secret |
                """.getBytes(StandardCharsets.UTF_8);

        ImportDocument document = extractor.extract("export.md", bytes);

        assertEquals(1, document.rows().size());
        assertEquals("私人邮箱", document.rows().getFirst().cells().get("名称"));
        assertEquals("网易", document.rows().getFirst().cells().get("平台"));
        assertEquals("user@163.com", document.rows().getFirst().cells().get("账号"));
        assertTrue(document.rows().getFirst().cells().containsKey("密码"));
    }

    @Test
    void parsesFreeformCredentialDumpWithoutOverSplitting() {
        byte[] bytes = """
                谷歌账号 (用于Claude Code)
                账号：rardinabbatielloxg930@gmail.com
                密码：n9d3qsoser2
                辅助邮箱：rardinabbatielloxg93048061@fitleror.shop
                2FA密钥：dbmrf7jzj4xccrdpvrwudm5fkcbgacno

                温馨提示：登录后请尽快绑定自己的信息

                Google 身份验证器教程
                https://docs.qq.com/doc/example1

                ChatGPT账号2
                账号：demo@example.com
                密码：secret2

                随便一段说明文字
                没有账号密码的段落应并入上一条
                """.getBytes(StandardCharsets.UTF_8);

        ImportDocument document = extractor.extract("AI编程工具账密.md", bytes);

        assertEquals(2, document.rows().size());
        Map<String, String> first = document.rows().getFirst().cells();
        assertEquals("谷歌账号 (用于Claude Code)", first.get("名称"));
        assertEquals("rardinabbatielloxg930@gmail.com", first.get("账号"));
        assertEquals("n9d3qsoser2", first.get("密码"));
        assertEquals("dbmrf7jzj4xccrdpvrwudm5fkcbgacno", first.get("2FA密钥"));
        assertTrue(document.rows().getFirst().rawText().contains("温馨提示"));
        assertEquals("demo@example.com", document.rows().get(1).cells().get("账号"));
        assertTrue(document.rows().get(1).rawText().contains("没有账号密码"));
    }
}
