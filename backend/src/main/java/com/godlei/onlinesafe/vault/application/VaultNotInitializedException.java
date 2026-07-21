package com.godlei.onlinesafe.vault.application;

public class VaultNotInitializedException extends RuntimeException {

    public VaultNotInitializedException() {
        super("尚未初始化保险箱密钥");
    }
}
