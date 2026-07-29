package com.godlei.onlinesafe.vaultimport.web;

public record AiConnectivityTestResponse(
        boolean ok,
        long latencyMs,
        String model,
        String replyPreview,
        String message
) {
    public static AiConnectivityTestResponse success(long latencyMs, String model, String replyPreview) {
        return new AiConnectivityTestResponse(true, latencyMs, model, replyPreview, "联通成功");
    }

    public static AiConnectivityTestResponse failure(long latencyMs, String model, String message) {
        return new AiConnectivityTestResponse(false, latencyMs, model, "", message);
    }
}
