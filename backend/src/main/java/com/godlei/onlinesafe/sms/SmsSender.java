package com.godlei.onlinesafe.sms;

import com.godlei.onlinesafe.auth.domain.SmsPurpose;

/**
 * 短信发送端口；生产走阿里云，测试/本地可替换实现。
 */
public interface SmsSender {

    void send(String phoneE164, SmsPurpose purpose, String code);
}
