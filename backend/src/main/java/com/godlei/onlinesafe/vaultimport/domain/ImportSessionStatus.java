package com.godlei.onlinesafe.vaultimport.domain;

public enum ImportSessionStatus {
    UPLOADING,
    PARSING,
    AI_MAPPING,
    READY,
    COMMITTING,
    COMMITTED,
    FAILED,
    DISCARDED
}
