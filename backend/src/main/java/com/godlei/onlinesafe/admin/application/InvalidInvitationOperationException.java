package com.godlei.onlinesafe.admin.application;

public class InvalidInvitationOperationException extends RuntimeException {

    private final String code;

    public InvalidInvitationOperationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
