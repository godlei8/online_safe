package com.godlei.onlinesafe.vaultimport.web;

/**
 * 可选覆盖项；未传或为空时使用已保存 / 环境变量配置。
 * API Key 传掩码 {@code ********} 或不传表示沿用已保存密钥。
 */
public record AiConnectivityTestRequest(
        String baseUrl,
        String model,
        String apiKey
) {
}
