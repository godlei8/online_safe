package com.godlei.onlinesafe.vaultimport.domain;

public enum ImportMode {
    FAST,
    AI;

    public static ImportMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return FAST;
        }
        String normalized = raw.trim().toUpperCase();
        if ("AI".equals(normalized) || "SMART".equals(normalized)) {
            return AI;
        }
        return FAST;
    }
}
