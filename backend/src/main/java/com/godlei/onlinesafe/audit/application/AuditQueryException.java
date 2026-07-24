package com.godlei.onlinesafe.audit.application;

import org.springframework.http.HttpStatus;

public class AuditQueryException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public AuditQueryException(HttpStatus status, String code, String message) {
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
