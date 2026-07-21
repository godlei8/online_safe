package com.godlei.onlinesafe.vault.application;

public class VaultItemNotFoundException extends RuntimeException {

    public VaultItemNotFoundException() {
        super("保险箱记录不存在");
    }
}
