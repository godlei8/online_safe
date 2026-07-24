package com.godlei.onlinesafe.auth.application;

public class ProfileException extends RuntimeException {

    private final String code;

    public ProfileException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
