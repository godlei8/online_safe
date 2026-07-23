package com.godlei.onlinesafe.systemtemplate.application;

public class SystemTemplateNotFoundException extends RuntimeException {

    public SystemTemplateNotFoundException() {
        super("系统模板不存在");
    }
}
