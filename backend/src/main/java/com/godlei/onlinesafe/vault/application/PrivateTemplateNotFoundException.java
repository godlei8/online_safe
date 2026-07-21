package com.godlei.onlinesafe.vault.application;

public class PrivateTemplateNotFoundException extends RuntimeException {

    public PrivateTemplateNotFoundException() {
        super("私人模板不存在");
    }
}
