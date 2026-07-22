package com.godlei.onlinesafe.sms;

import com.godlei.onlinesafe.auth.domain.SmsPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 本地开发用：将验证码写入日志，不调用外部网关。
 */
public class LoggingSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);

    @Override
    public void send(String phoneE164, SmsPurpose purpose, String code) {
        log.warn("[SMS-LOGGING] phone={} purpose={} code={}", phoneE164, purpose, code);
    }
}
