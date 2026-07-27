package com.godlei.onlinesafe.datarecovery.application;

public class DataRecoveryException extends RuntimeException {

    private final String code;

    public DataRecoveryException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
