package com.godlei.onlinesafe.vaultimport.application;

public class VaultImportException extends RuntimeException {

    private final String code;

    public VaultImportException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
