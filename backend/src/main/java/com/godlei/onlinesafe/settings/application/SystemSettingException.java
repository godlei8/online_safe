package com.godlei.onlinesafe.settings.application;

import org.springframework.http.HttpStatus;

public class SystemSettingException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public SystemSettingException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
