package com.godlei.onlinesafe.vault.application;

public class VaultRevisionConflictException extends RuntimeException {

    public VaultRevisionConflictException() {
        super("记录已被其他设备更新，请刷新后重试");
    }
}
