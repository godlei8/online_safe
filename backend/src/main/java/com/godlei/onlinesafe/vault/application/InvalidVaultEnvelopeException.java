package com.godlei.onlinesafe.vault.application;

public class InvalidVaultEnvelopeException extends RuntimeException {

    private final String code;

    public InvalidVaultEnvelopeException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
