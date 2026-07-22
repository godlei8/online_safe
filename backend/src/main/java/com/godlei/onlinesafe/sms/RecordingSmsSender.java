package com.godlei.onlinesafe.sms;

import com.godlei.onlinesafe.auth.domain.SmsPurpose;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 测试用：记录已发送验证码明文，供集成测试断言。
 */
public class RecordingSmsSender implements SmsSender {

    public record SentMessage(String phoneE164, SmsPurpose purpose, String code) {
    }

    private final ConcurrentMap<String, List<SentMessage>> messages = new ConcurrentHashMap<>();

    @Override
    public void send(String phoneE164, SmsPurpose purpose, String code) {
        messages.compute(key(phoneE164, purpose), (ignored, existing) -> {
            List<SentMessage> list = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
            list.add(new SentMessage(phoneE164, purpose, code));
            return list;
        });
    }

    public String requireLatestCode(String phoneE164, SmsPurpose purpose) {
        List<SentMessage> list = messages.get(key(phoneE164, purpose));
        if (list == null || list.isEmpty()) {
            throw new IllegalStateException("未找到已发送的验证码: " + phoneE164 + "/" + purpose);
        }
        return list.get(list.size() - 1).code();
    }

    public void clear() {
        messages.clear();
    }

    private static String key(String phoneE164, SmsPurpose purpose) {
        return phoneE164 + "|" + purpose.name();
    }
}
