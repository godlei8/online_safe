package com.godlei.onlinesafe.vault.application;

public class VaultAlreadyInitializedException extends RuntimeException {

    public VaultAlreadyInitializedException() {
        super("保险箱密钥已初始化");
    }
}
