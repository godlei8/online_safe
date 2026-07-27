package com.godlei.onlinesafe.settings.application;

import com.godlei.onlinesafe.audit.domain.AuditRiskLevel;
import com.godlei.onlinesafe.settings.domain.RegistrationMode;
import com.godlei.onlinesafe.settings.domain.SystemSettingDefinition;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class SystemSettingRegistry {

    public static final String REGISTRATION_MODE = "registration.mode";
    public static final String PASSWORD_MIN_LENGTH = "registration.password_min_length";
    public static final String USERNAME_COOLDOWN_DAYS = "profile.username_change_cooldown_days";
    public static final String INVITATION_DEFAULT_VALID_DAYS = "invitation.default_valid_days";
    public static final String INVITATION_DEFAULT_MAX_USES = "invitation.default_max_uses";
    public static final String AUDIT_RETENTION_DAYS = "security.audit_retention_days";
    public static final String MAX_ACTIVE_USER_SESSIONS = "security.max_active_user_sessions";
    public static final String RECYCLE_BIN_RETENTION_DAYS = "security.recycle_bin_retention_days";
    public static final String BACKUP_REMINDER_DAYS = "security.backup_reminder_days";

    public static final String GROUP_ACCOUNT = "ACCOUNT";
    public static final String GROUP_INVITATION = "INVITATION";
    public static final String GROUP_SECURITY_LOG = "SECURITY_LOG";
    public static final String GROUP_SESSION = "SESSION";
    public static final String GROUP_DATA_SECURITY = "DATA_SECURITY";

    private final Map<String, SystemSettingDefinition> definitions;

    public SystemSettingRegistry() {
        Map<String, SystemSettingDefinition> map = new LinkedHashMap<>();
        put(map, SystemSettingDefinition.enumSetting(
                REGISTRATION_MODE,
                GROUP_ACCOUNT,
                "注册模式",
                RegistrationMode.SMS_VERIFIED.name(),
                List.of(
                        RegistrationMode.CLOSED.name(),
                        RegistrationMode.SMS_VERIFIED.name(),
                        RegistrationMode.INVITE_AND_SMS.name()
                ),
                AuditRiskLevel.HIGH
        ));
        put(map, SystemSettingDefinition.intSetting(
                PASSWORD_MIN_LENGTH,
                GROUP_ACCOUNT,
                "密码最小长度",
                8,
                8,
                32,
                AuditRiskLevel.WARNING
        ));
        put(map, SystemSettingDefinition.intSetting(
                USERNAME_COOLDOWN_DAYS,
                GROUP_ACCOUNT,
                "用户名修改冷却期（天）",
                30,
                7,
                180,
                AuditRiskLevel.WARNING
        ));
        put(map, SystemSettingDefinition.intSetting(
                MAX_ACTIVE_USER_SESSIONS,
                GROUP_SESSION,
                "个人最大活跃会话数",
                2,
                1,
                10,
                AuditRiskLevel.WARNING
        ));
        put(map, SystemSettingDefinition.intSetting(
                INVITATION_DEFAULT_VALID_DAYS,
                GROUP_INVITATION,
                "邀请码默认有效天数",
                7,
                1,
                90,
                AuditRiskLevel.INFO
        ));
        put(map, SystemSettingDefinition.intSetting(
                INVITATION_DEFAULT_MAX_USES,
                GROUP_INVITATION,
                "邀请码默认可用次数",
                1,
                1,
                1000,
                AuditRiskLevel.INFO
        ));
        put(map, SystemSettingDefinition.enumSetting(
                AUDIT_RETENTION_DAYS,
                GROUP_SECURITY_LOG,
                "安全日志保留天数",
                180,
                List.of(90, 180, 365),
                AuditRiskLevel.HIGH
        ));
        put(map, SystemSettingDefinition.enumSetting(
                RECYCLE_BIN_RETENTION_DAYS,
                GROUP_DATA_SECURITY,
                "回收站保留天数",
                30,
                List.of(7, 30, 90),
                AuditRiskLevel.HIGH
        ));
        put(map, SystemSettingDefinition.enumSetting(
                BACKUP_REMINDER_DAYS,
                GROUP_DATA_SECURITY,
                "备份提醒天数",
                30,
                List.of(30, 60, 90),
                AuditRiskLevel.INFO
        ));
        this.definitions = Map.copyOf(map);
    }

    public Optional<SystemSettingDefinition> find(String key) {
        return Optional.ofNullable(definitions.get(key));
    }

    public Collection<SystemSettingDefinition> all() {
        return definitions.values();
    }

    public List<SystemSettingDefinition> byGroup(String group) {
        return definitions.values().stream().filter(item -> item.group().equals(group)).toList();
    }

    private static void put(Map<String, SystemSettingDefinition> map, SystemSettingDefinition definition) {
        map.put(definition.key(), definition);
    }
}
