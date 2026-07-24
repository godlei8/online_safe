package com.godlei.onlinesafe.settings.application;

import com.godlei.onlinesafe.cos.CosProperties;
import com.godlei.onlinesafe.sms.SmsProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SystemSettingCapabilityService {

    private final SmsProperties smsProperties;
    private final CosProperties cosProperties;
    private final boolean sessionCookieSecure;

    public SystemSettingCapabilityService(
            SmsProperties smsProperties,
            CosProperties cosProperties,
            @Value("${server.servlet.session.cookie.secure:true}") boolean sessionCookieSecure
    ) {
        this.smsProperties = smsProperties;
        this.cosProperties = cosProperties;
        this.sessionCookieSecure = sessionCookieSecure;
    }

    public Map<String, Object> capabilities() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sms", Map.of(
                "label", "短信服务",
                "status", smsStatus(),
                "statusLabel", smsStatusLabel()
        ));
        map.put("avatarStorage", Map.of(
                "label", "头像存储",
                "status", avatarStatus(),
                "statusLabel", avatarStatusLabel()
        ));
        map.put("adminMfa", Map.of(
                "label", "管理员二次验证",
                "status", "NOT_IMPLEMENTED",
                "statusLabel", "尚未实现"
        ));
        map.put("secureCookie", Map.of(
                "label", "安全 Cookie",
                "status", sessionCookieSecure ? "HTTPS_REQUIRED" : "LOCAL_DEV",
                "statusLabel", sessionCookieSecure ? "已要求 HTTPS" : "本地开发模式"
        ));
        map.put("sessionTimeout", Map.of(
                "label", "会话超时",
                "status", "60M",
                "statusLabel", "60 分钟"
        ));
        map.put("auditWrite", Map.of(
                "label", "安全日志写入",
                "status", "OK",
                "statusLabel", "正常"
        ));
        return map;
    }

    private String smsStatus() {
        if (!"aliyun".equalsIgnoreCase(smsProperties.provider())) {
            return "LOGGING";
        }
        if (smsProperties.accessKeyId().isBlank() || smsProperties.signName().isBlank()) {
            return "NOT_CONFIGURED";
        }
        return "AVAILABLE";
    }

    private String smsStatusLabel() {
        return switch (smsStatus()) {
            case "AVAILABLE" -> "可用";
            case "LOGGING" -> "日志模式（开发）";
            default -> "未配置";
        };
    }

    private String avatarStatus() {
        if ("tencent".equalsIgnoreCase(cosProperties.provider())) {
            if (cosProperties.secretId().isBlank() || cosProperties.bucket().isBlank()) {
                return "NOT_CONFIGURED";
            }
            return "TENCENT_COS";
        }
        return "LOCAL";
    }

    private String avatarStatusLabel() {
        return switch (avatarStatus()) {
            case "TENCENT_COS" -> "腾讯云 COS 可用";
            case "LOCAL" -> "本地开发存储";
            default -> "未配置";
        };
    }
}
