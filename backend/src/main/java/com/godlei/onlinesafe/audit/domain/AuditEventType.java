package com.godlei.onlinesafe.audit.domain;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public enum AuditEventType {
    USER_LOGIN_SUCCEEDED("个人用户登录成功", AuditCategory.AUTH, AuditRiskLevel.INFO, Set.of()),
    USER_LOGIN_FAILED("个人用户登录失败", AuditCategory.AUTH, AuditRiskLevel.WARNING, Set.of()),
    USER_LOGOUT_SUCCEEDED("个人用户退出登录", AuditCategory.AUTH, AuditRiskLevel.INFO, Set.of()),
    ADMIN_LOGIN_SUCCEEDED("管理员登录成功", AuditCategory.AUTH, AuditRiskLevel.INFO, Set.of()),
    ADMIN_LOGIN_FAILED("管理员登录失败", AuditCategory.AUTH, AuditRiskLevel.WARNING, Set.of()),
    ADMIN_LOGOUT_SUCCEEDED("管理员退出登录", AuditCategory.AUTH, AuditRiskLevel.INFO, Set.of()),
    USER_REGISTERED("用户注册成功", AuditCategory.AUTH, AuditRiskLevel.INFO, Set.of("inviteUsed")),
    USER_REGISTRATION_FAILED("用户注册失败", AuditCategory.AUTH, AuditRiskLevel.WARNING, Set.of()),
    SMS_CODE_SEND_BLOCKED("验证码发送被限流", AuditCategory.AUTH, AuditRiskLevel.WARNING, Set.of("purpose")),
    PASSWORD_RESET_SUCCEEDED("登录密码重置成功", AuditCategory.AUTH, AuditRiskLevel.WARNING, Set.of()),
    PASSWORD_RESET_FAILED("登录密码重置失败", AuditCategory.AUTH, AuditRiskLevel.WARNING, Set.of()),

    USER_DISABLED_BY_ADMIN("管理员禁用用户", AuditCategory.ACCOUNT, AuditRiskLevel.HIGH, Set.of("previousStatus", "newStatus")),
    USER_ENABLED_BY_ADMIN("管理员启用用户", AuditCategory.ACCOUNT, AuditRiskLevel.WARNING, Set.of("previousStatus", "newStatus")),
    USER_SESSIONS_REVOKED_BY_ADMIN("管理员使用户会话失效", AuditCategory.SESSION, AuditRiskLevel.WARNING, Set.of("sessionsRevoked")),
    USER_SESSION_REVOKED("用户退出指定设备", AuditCategory.SESSION, AuditRiskLevel.WARNING, Set.of("sessionsRevoked")),
    USER_OTHER_SESSIONS_REVOKED("用户退出其他设备", AuditCategory.SESSION, AuditRiskLevel.WARNING, Set.of("sessionsRevoked", "reason")),
    USER_ALL_SESSIONS_REVOKED("用户退出全部设备", AuditCategory.SESSION, AuditRiskLevel.HIGH, Set.of("sessionsRevoked")),
    USER_SESSION_LIMIT_REPLACED("达到会话上限替换旧会话", AuditCategory.SESSION, AuditRiskLevel.WARNING, Set.of("maxSessions")),
    USERNAME_CHANGED("用户修改用户名", AuditCategory.ACCOUNT, AuditRiskLevel.INFO, Set.of("previousUsernameHint", "newUsernameHint")),
    USER_AVATAR_CHANGED("用户修改头像", AuditCategory.ACCOUNT, AuditRiskLevel.INFO, Set.of()),

    INVITATION_CREATED("创建邀请码", AuditCategory.INVITATION, AuditRiskLevel.INFO, Set.of("codeHint", "maxUses", "validDays")),
    INVITATION_DELETED("删除邀请码", AuditCategory.INVITATION, AuditRiskLevel.WARNING, Set.of("codeHint")),
    INVITATION_PLAIN_CODE_VIEWED("查看邀请码明文", AuditCategory.INVITATION, AuditRiskLevel.WARNING, Set.of("codeHint")),
    INVITATION_REDEEMED("邀请码被使用", AuditCategory.INVITATION, AuditRiskLevel.INFO, Set.of("codeHint")),

    ANNOUNCEMENT_CREATED("创建公告草稿", AuditCategory.ANNOUNCEMENT, AuditRiskLevel.INFO, Set.of("title")),
    ANNOUNCEMENT_UPDATED("修改公告", AuditCategory.ANNOUNCEMENT, AuditRiskLevel.INFO, Set.of("title")),
    ANNOUNCEMENT_PUBLISHED("发布公告", AuditCategory.ANNOUNCEMENT, AuditRiskLevel.WARNING, Set.of("title")),
    ANNOUNCEMENT_OFFLINED("下线公告", AuditCategory.ANNOUNCEMENT, AuditRiskLevel.WARNING, Set.of("title")),

    SYSTEM_TEMPLATE_CREATED("创建系统模板", AuditCategory.TEMPLATE, AuditRiskLevel.INFO, Set.of("name", "status")),
    SYSTEM_TEMPLATE_UPDATED("修改系统模板", AuditCategory.TEMPLATE, AuditRiskLevel.INFO, Set.of("name", "status")),
    SYSTEM_TEMPLATE_PUBLISHED("发布系统模板", AuditCategory.TEMPLATE, AuditRiskLevel.WARNING, Set.of("name", "status")),
    SYSTEM_TEMPLATE_OFFLINED("下线系统模板", AuditCategory.TEMPLATE, AuditRiskLevel.WARNING, Set.of("name", "status")),
    SYSTEM_TEMPLATE_DELETED("删除系统模板草稿", AuditCategory.TEMPLATE, AuditRiskLevel.WARNING, Set.of("name")),

    SYSTEM_SETTINGS_UPDATED("修改系统设置", AuditCategory.SETTINGS, AuditRiskLevel.WARNING, Set.of("changedKeys")),
    SECURITY_LOG_RETENTION_CHANGED("修改日志保留期限", AuditCategory.SETTINGS, AuditRiskLevel.HIGH, Set.of("previousDays", "newDays")),
    SECURITY_LOG_PURGE_SUCCEEDED("安全日志定时清理完成", AuditCategory.SYSTEM, AuditRiskLevel.INFO, Set.of("deletedCount", "cutoffAt")),
    SECURITY_LOG_PURGE_FAILED("安全日志定时清理失败", AuditCategory.SYSTEM, AuditRiskLevel.HIGH, Set.of("errorCode")),
    AUTH_FAILURES_AGGREGATED("高频认证失败已聚合", AuditCategory.AUTH, AuditRiskLevel.HIGH, Set.of("windowSeconds")),

    USER_REAUTH_SUCCEEDED("二次验证成功", AuditCategory.AUTH, AuditRiskLevel.INFO, Set.of()),
    USER_REAUTH_FAILED("二次验证失败", AuditCategory.AUTH, AuditRiskLevel.WARNING, Set.of("reason")),
    USER_BACKUP_SNAPSHOT_CREATED("创建加密备份快照", AuditCategory.DATA_RECOVERY, AuditRiskLevel.WARNING, Set.of("itemCount", "templateCount", "includesTrash", "formatVersion")),
    USER_BACKUP_RESTORE_STARTED("开始备份恢复", AuditCategory.DATA_RECOVERY, AuditRiskLevel.WARNING, Set.of("totalCount", "formatVersion")),
    USER_BACKUP_RESTORE_SUCCEEDED("备份恢复成功", AuditCategory.DATA_RECOVERY, AuditRiskLevel.WARNING, Set.of("createdCount", "restoredCount", "skippedCount")),
    USER_BACKUP_RESTORE_FAILED("备份恢复失败", AuditCategory.DATA_RECOVERY, AuditRiskLevel.HIGH, Set.of("errorCode", "processedCount")),
    USER_TRASH_ASSET_RESTORED("回收站资产已恢复", AuditCategory.DATA_RECOVERY, AuditRiskLevel.INFO, Set.of("assetType")),
    USER_TRASH_ASSET_PURGED("回收站资产永久删除", AuditCategory.DATA_RECOVERY, AuditRiskLevel.HIGH, Set.of("assetType")),
    USER_TRASH_EMPTIED("清空回收站", AuditCategory.DATA_RECOVERY, AuditRiskLevel.HIGH, Set.of("itemCount", "templateCount")),
    VAULT_TRASH_PURGE_SUCCEEDED("回收站定时清理完成", AuditCategory.SYSTEM, AuditRiskLevel.INFO, Set.of("deletedCount", "cutoffAt")),
    VAULT_TRASH_PURGE_FAILED("回收站定时清理失败", AuditCategory.SYSTEM, AuditRiskLevel.HIGH, Set.of("errorCode")),
    VAULT_INTEGRITY_SCAN_SUCCEEDED("保险箱完整性扫描完成", AuditCategory.SYSTEM, AuditRiskLevel.INFO, Set.of("checkedCount", "failedCount")),
    VAULT_INTEGRITY_SCAN_FAILED("保险箱完整性扫描失败", AuditCategory.SYSTEM, AuditRiskLevel.HIGH, Set.of("errorCode")),

    VAULT_IMPORT_CREATED("创建智能导入会话", AuditCategory.DATA_RECOVERY, AuditRiskLevel.INFO, Set.of("sessionId", "fileFormat", "byteSize", "importMode")),
    VAULT_IMPORT_PARSED("智能导入文件已解析", AuditCategory.DATA_RECOVERY, AuditRiskLevel.INFO, Set.of("sessionId", "rowCount", "fileFormat", "importMode")),
    VAULT_IMPORT_AI_DONE("智能导入 AI 映射完成", AuditCategory.DATA_RECOVERY, AuditRiskLevel.INFO, Set.of("sessionId", "readyCount", "needsReviewCount", "skippedCount", "modelName", "degraded", "importMode")),
    VAULT_IMPORT_COMMITTED("智能导入已提交", AuditCategory.DATA_RECOVERY, AuditRiskLevel.WARNING, Set.of("sessionId", "succeededCount", "failedCount")),
    VAULT_IMPORT_FAILED("智能导入失败", AuditCategory.DATA_RECOVERY, AuditRiskLevel.HIGH, Set.of("sessionId", "errorCode")),
    VAULT_IMPORT_DISCARDED("智能导入会话已放弃", AuditCategory.DATA_RECOVERY, AuditRiskLevel.INFO, Set.of("sessionId"));

    private final String labelZh;
    private final AuditCategory category;
    private final AuditRiskLevel defaultRisk;
    private final Set<String> allowedMetadataKeys;

    AuditEventType(String labelZh, AuditCategory category, AuditRiskLevel defaultRisk, Set<String> allowedMetadataKeys) {
        this.labelZh = labelZh;
        this.category = category;
        this.defaultRisk = defaultRisk;
        this.allowedMetadataKeys = allowedMetadataKeys;
    }

    public String labelZh() {
        return labelZh;
    }

    public AuditCategory category() {
        return category;
    }

    public AuditRiskLevel defaultRisk() {
        return defaultRisk;
    }

    public Set<String> allowedMetadataKeys() {
        return allowedMetadataKeys;
    }

    public static Optional<AuditEventType> fromCode(String code) {
        return Arrays.stream(values()).filter(item -> item.name().equals(code)).findFirst();
    }

    public static Map<String, Object> dictionary() {
        Map<String, Object> map = new LinkedHashMap<>();
        for (AuditEventType type : values()) {
            map.put(type.name(), Map.of(
                    "code", type.name(),
                    "labelZh", type.labelZh,
                    "category", type.category.name(),
                    "defaultRisk", type.defaultRisk.name()
            ));
        }
        return map;
    }
}
