package com.godlei.onlinesafe.auth.application;

public class SmsException extends RuntimeException {

    private final String code;

    public SmsException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
