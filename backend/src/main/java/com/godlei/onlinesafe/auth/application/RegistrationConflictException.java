package com.godlei.onlinesafe.auth.application;

public class RegistrationConflictException extends RuntimeException {

    public RegistrationConflictException() {
        super("手机号或用户名已被注册");
    }
}
