package com.godlei.onlinesafe.announcement.application;

public class InvalidAnnouncementOperationException extends RuntimeException {

    private final String code;

    public InvalidAnnouncementOperationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
