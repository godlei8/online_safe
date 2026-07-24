package com.godlei.onlinesafe.session.application;

import org.springframework.http.HttpStatus;

public class SessionException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public SessionException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
