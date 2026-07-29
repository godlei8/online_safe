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
    public static final String SMART_IMPORT_ENABLED = "feature.vault_smart_import_enabled";
    public static final String SMART_IMPORT_CONFIDENCE_THRESHOLD = "feature.vault_smart_import_confidence_threshold";
    public static final String SMART_IMPORT_MAX_FILE_BYTES = "feature.vault_smart_import_max_file_bytes";
    public static final String SMART_IMPORT_MAX_ROWS = "feature.vault_smart_import_max_rows";
    public static final String SMART_IMPORT_SESSION_TTL_MINUTES = "feature.vault_smart_import_session_ttl_minutes";
    public static final String AI_MODEL_ENABLED = "ai.model.enabled";
    public static final String AI_MODEL_BASE_URL = "ai.model.base_url";
    public static final String AI_MODEL_NAME = "ai.model.name";
    public static final String AI_MODEL_API_KEY = "ai.model.api_key";
    public static final String AI_MODEL_TIMEOUT_SECONDS = "ai.model.timeout_seconds";
    public static final String AI_MODEL_MAX_RETRIES = "ai.model.max_retries";

    public static final String GROUP_ACCOUNT = "ACCOUNT";
    public static final String GROUP_INVITATION = "INVITATION";
    public static final String GROUP_SECURITY_LOG = "SECURITY_LOG";
    public static final String GROUP_SESSION = "SESSION";
    public static final String GROUP_DATA_SECURITY = "DATA_SECURITY";
    public static final String GROUP_AI = "AI";
    /** @deprecated 使用 {@link #GROUP_AI} */
    public static final String GROUP_FEATURE = GROUP_AI;

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
        put(map, SystemSettingDefinition.booleanSetting(
                SMART_IMPORT_ENABLED,
                GROUP_AI,
                "启用智能导入",
                true,
                AuditRiskLevel.HIGH
        ));
        put(map, SystemSettingDefinition.intSetting(
                SMART_IMPORT_CONFIDENCE_THRESHOLD,
                GROUP_AI,
                "智能导入置信度阈值（%）",
                75,
                50,
                95,
                AuditRiskLevel.WARNING
        ));
        put(map, SystemSettingDefinition.intSetting(
                SMART_IMPORT_MAX_FILE_BYTES,
                GROUP_AI,
                "智能导入单文件大小上限（字节）",
                5_242_880,
                1_048_576,
                10_485_760,
                AuditRiskLevel.WARNING
        ));
        put(map, SystemSettingDefinition.intSetting(
                SMART_IMPORT_MAX_ROWS,
                GROUP_AI,
                "智能导入单次最大条数",
                200,
                10,
                500,
                AuditRiskLevel.WARNING
        ));
        put(map, SystemSettingDefinition.intSetting(
                SMART_IMPORT_SESSION_TTL_MINUTES,
                GROUP_AI,
                "智能导入会话有效分钟数",
                30,
                10,
                120,
                AuditRiskLevel.INFO
        ));
        put(map, SystemSettingDefinition.booleanSetting(
                AI_MODEL_ENABLED,
                GROUP_AI,
                "启用大模型调用",
                false,
                AuditRiskLevel.HIGH
        ));
        put(map, SystemSettingDefinition.stringSetting(
                AI_MODEL_BASE_URL,
                GROUP_AI,
                "模型 API 根地址（OpenAI 兼容，含 /v1）",
                "",
                AuditRiskLevel.HIGH
        ));
        put(map, SystemSettingDefinition.stringSetting(
                AI_MODEL_NAME,
                GROUP_AI,
                "模型名称",
                "",
                AuditRiskLevel.HIGH
        ));
        put(map, SystemSettingDefinition.secretStringSetting(
                AI_MODEL_API_KEY,
                GROUP_AI,
                "模型 API Key",
                AuditRiskLevel.HIGH
        ));
        put(map, SystemSettingDefinition.intSetting(
                AI_MODEL_TIMEOUT_SECONDS,
                GROUP_AI,
                "模型请求超时（秒）",
                60,
                10,
                300,
                AuditRiskLevel.WARNING
        ));
        put(map, SystemSettingDefinition.intSetting(
                AI_MODEL_MAX_RETRIES,
                GROUP_AI,
                "模型失败重试次数",
                2,
                0,
                5,
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
