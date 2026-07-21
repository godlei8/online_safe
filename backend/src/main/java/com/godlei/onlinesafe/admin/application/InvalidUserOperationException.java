package com.godlei.onlinesafe.admin.application;

public class InvalidUserOperationException extends RuntimeException {

    private final String code;

    public InvalidUserOperationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
