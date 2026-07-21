package com.godlei.onlinesafe.admin.application;

public class InvitationNotFoundException extends RuntimeException {
    public InvitationNotFoundException() {
        super("邀请码不存在");
    }
}
