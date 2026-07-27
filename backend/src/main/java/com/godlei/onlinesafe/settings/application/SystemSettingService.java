package com.godlei.onlinesafe.settings.application;

import com.godlei.onlinesafe.audit.application.SecurityAuditService;
import com.godlei.onlinesafe.audit.domain.AuditEventType;
import com.godlei.onlinesafe.audit.domain.AuditResult;
import com.godlei.onlinesafe.audit.domain.AuditRiskLevel;
import com.godlei.onlinesafe.settings.domain.RegistrationMode;
import com.godlei.onlinesafe.settings.domain.SettingValueType;
import com.godlei.onlinesafe.settings.domain.SystemSetting;
import com.godlei.onlinesafe.settings.domain.SystemSettingDefinition;
import com.godlei.onlinesafe.settings.infrastructure.SystemSettingRepository;
import com.godlei.onlinesafe.settings.web.RegistrationPolicyResponse;
import com.godlei.onlinesafe.settings.web.SystemSettingChangeRequest;
import com.godlei.onlinesafe.settings.web.SystemSettingItemResponse;
import com.godlei.onlinesafe.settings.web.SystemSettingUpdateRequest;
import com.godlei.onlinesafe.settings.web.SystemSettingValidateResponse;
import com.godlei.onlinesafe.settings.web.SystemSettingsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class SystemSettingService {

    private static final Logger log = LoggerFactory.getLogger(SystemSettingService.class);
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final SystemSettingRegistry registry;
    private final SystemSettingRepository repository;
    private final SystemSettingCapabilityService capabilityService;
    private final SecurityAuditService securityAuditService;
    private final Clock clock;
    private final AtomicReference<CacheEntry> cache = new AtomicReference<>();

    public SystemSettingService(
            SystemSettingRegistry registry,
            SystemSettingRepository repository,
            SystemSettingCapabilityService capabilityService,
            SecurityAuditService securityAuditService,
            Clock clock
    ) {
        this.registry = registry;
        this.repository = repository;
        this.capabilityService = capabilityService;
        this.securityAuditService = securityAuditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SystemSettingsResponse getAll() {
        Map<String, EffectiveValue> values = loadEffective();
        Map<String, List<SystemSettingItemResponse>> groups = new LinkedHashMap<>();
        for (SystemSettingDefinition definition : registry.all()) {
            EffectiveValue effective = values.get(definition.key());
            groups.computeIfAbsent(definition.group(), key -> new ArrayList<>())
                    .add(new SystemSettingItemResponse(
                            definition.key(),
                            definition.labelZh(),
                            definition.type().name(),
                            effective.value(),
                            definition.defaultValue(),
                            effective.version(),
                            definition.min(),
                            definition.max(),
                            definition.allowedValues().stream().map(String::valueOf).toList(),
                            definition.editable(),
                            definition.riskLevel().name()
                    ));
        }
        return new SystemSettingsResponse(groups, capabilityService.capabilities());
    }

    @Transactional(readOnly = true)
    public SystemSettingValidateResponse validate(SystemSettingUpdateRequest request) {
        List<ParsedChange> changes = parseChanges(request);
        return buildValidateResponse(changes);
    }

    @Transactional
    public SystemSettingsResponse update(String adminId, SystemSettingUpdateRequest request) {
        List<ParsedChange> changes = parseChanges(request);
        SystemSettingValidateResponse validation = buildValidateResponse(changes);
        if (!validation.valid()) {
            throw new SystemSettingException(HttpStatus.BAD_REQUEST, "SETTING_INVALID", "系统设置校验失败");
        }

        Instant now = clock.instant();
        List<String> changedKeys = new ArrayList<>();
        Integer previousRetention = null;
        Integer newRetention = null;

        for (ParsedChange change : changes) {
            SystemSetting existing = repository.findById(change.definition().key()).orElse(null);
            long expected = change.expectedVersion() == null ? 0L : change.expectedVersion();
            if (existing == null) {
                if (expected != 0L) {
                    throw new SystemSettingException(HttpStatus.CONFLICT, "SETTING_VERSION_CONFLICT", "设置版本冲突，请刷新后重试");
                }
                repository.save(SystemSetting.create(
                        change.definition().key(),
                        change.value(),
                        change.definition().type(),
                        adminId,
                        now
                ));
            } else {
                if (existing.getVersion() != expected) {
                    throw new SystemSettingException(HttpStatus.CONFLICT, "SETTING_VERSION_CONFLICT", "设置版本冲突，请刷新后重试");
                }
                if (SystemSettingRegistry.AUDIT_RETENTION_DAYS.equals(change.definition().key())) {
                    previousRetention = toInt(existing.getValueJson());
                    newRetention = toInt(change.value());
                }
                existing.updateValue(change.value(), adminId, now);
                repository.save(existing);
            }
            changedKeys.add(change.definition().key());
            if (SystemSettingRegistry.AUDIT_RETENTION_DAYS.equals(change.definition().key()) && previousRetention == null) {
                previousRetention = toInt(change.definition().defaultValue());
                newRetention = toInt(change.value());
            }
        }

        SecurityAuditService.ActorSnapshot actor = securityAuditService.requireAdminActor();
        securityAuditService.recordInTx(
                AuditEventType.SYSTEM_SETTINGS_UPDATED,
                AuditResult.SUCCESS,
                actor.type(),
                actor.id(),
                actor.label(),
                "SETTINGS",
                "batch",
                "系统设置",
                null,
                Map.of("changedKeys", String.join(",", changedKeys))
        );

        if (previousRetention != null && newRetention != null && newRetention < previousRetention) {
            securityAuditService.recordInTx(
                    AuditEventType.SECURITY_LOG_RETENTION_CHANGED,
                    AuditResult.SUCCESS,
                    actor.type(),
                    actor.id(),
                    actor.label(),
                    "SETTINGS",
                    SystemSettingRegistry.AUDIT_RETENTION_DAYS,
                    "日志保留期限",
                    null,
                    Map.of("previousDays", previousRetention, "newDays", newRetention)
            );
        }

        invalidateCache();
        return getAll();
    }

    public RegistrationPolicyResponse registrationPolicy() {
        RegistrationMode mode = registrationModeSafe();
        int passwordMin = passwordMinLengthSafe();
        boolean enabled = mode != RegistrationMode.CLOSED;
        return new RegistrationPolicyResponse(
                enabled,
                mode.name(),
                enabled,
                mode == RegistrationMode.INVITE_AND_SMS,
                passwordMin
        );
    }

    public RegistrationMode registrationModeSafe() {
        try {
            Object value = effectiveValue(SystemSettingRegistry.REGISTRATION_MODE);
            return RegistrationMode.valueOf(String.valueOf(value));
        } catch (RuntimeException exception) {
            log.error("读取注册模式失败，按 CLOSED 处理");
            return RegistrationMode.CLOSED;
        }
    }

    public int passwordMinLengthSafe() {
        try {
            return Math.max(8, toInt(effectiveValue(SystemSettingRegistry.PASSWORD_MIN_LENGTH)));
        } catch (RuntimeException exception) {
            return 8;
        }
    }

    public int usernameCooldownDays() {
        return toInt(effectiveValue(SystemSettingRegistry.USERNAME_COOLDOWN_DAYS));
    }

    /** 个人用户最大活跃会话数，非法值回退默认 2。 */
    public int maxActiveUserSessions() {
        try {
            int value = toInt(effectiveValue(SystemSettingRegistry.MAX_ACTIVE_USER_SESSIONS));
            if (value < 1 || value > 10) {
                return 2;
            }
            return value;
        } catch (RuntimeException exception) {
            return 2;
        }
    }

    public int invitationDefaultValidDays() {
        return toInt(effectiveValue(SystemSettingRegistry.INVITATION_DEFAULT_VALID_DAYS));
    }

    public int invitationDefaultMaxUses() {
        return toInt(effectiveValue(SystemSettingRegistry.INVITATION_DEFAULT_MAX_USES));
    }

    /** 读取失败返回 null，清理任务应跳过。 */
    public Integer auditRetentionDaysOrNull() {
        try {
            Object value = effectiveValue(SystemSettingRegistry.AUDIT_RETENTION_DAYS);
            int days = toInt(value);
            if (days == 90 || days == 180 || days == 365) {
                return days;
            }
            return null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** 读取失败返回 null，回收站清理任务应跳过。 */
    public Integer recycleBinRetentionDaysOrNull() {
        try {
            Object value = effectiveValue(SystemSettingRegistry.RECYCLE_BIN_RETENTION_DAYS);
            int days = toInt(value);
            if (days == 7 || days == 30 || days == 90) {
                return days;
            }
            return null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public int recycleBinRetentionDaysSafe() {
        Integer days = recycleBinRetentionDaysOrNull();
        return days == null ? 30 : days;
    }

    public int backupReminderDaysSafe() {
        try {
            int days = toInt(effectiveValue(SystemSettingRegistry.BACKUP_REMINDER_DAYS));
            if (days == 30 || days == 60 || days == 90) {
                return days;
            }
            return 30;
        } catch (RuntimeException exception) {
            return 30;
        }
    }

    public void invalidateCache() {
        cache.set(null);
    }

    private Object effectiveValue(String key) {
        return loadEffective().get(key).value();
    }

    private Map<String, EffectiveValue> loadEffective() {
        CacheEntry cached = cache.get();
        Instant now = clock.instant();
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.values();
        }
        Map<String, SystemSetting> db = new LinkedHashMap<>();
        repository.findAll().forEach(item -> {
            if (registry.find(item.getSettingKey()).isEmpty()) {
                log.warn("忽略未知系统设置键 {}", item.getSettingKey());
                return;
            }
            db.put(item.getSettingKey(), item);
        });
        Map<String, EffectiveValue> values = new LinkedHashMap<>();
        for (SystemSettingDefinition definition : registry.all()) {
            SystemSetting stored = db.get(definition.key());
            if (stored == null) {
                values.put(definition.key(), new EffectiveValue(definition.defaultValue(), 0L));
            } else {
                values.put(definition.key(), new EffectiveValue(normalizeStored(definition, stored.getValueJson()), stored.getVersion()));
            }
        }
        cache.set(new CacheEntry(values, now.plus(CACHE_TTL)));
        return values;
    }

    private List<ParsedChange> parseChanges(SystemSettingUpdateRequest request) {
        if (request == null || request.changes() == null || request.changes().isEmpty()) {
            throw new SystemSettingException(HttpStatus.BAD_REQUEST, "SETTING_EMPTY", "请至少提交一项设置变更");
        }
        String group = null;
        List<ParsedChange> parsed = new ArrayList<>();
        for (SystemSettingChangeRequest change : request.changes()) {
            SystemSettingDefinition definition = registry.find(change.key())
                    .orElseThrow(() -> new SystemSettingException(
                            HttpStatus.BAD_REQUEST, "SETTING_UNKNOWN", "未知设置项: " + change.key()));
            if (!definition.editable()) {
                throw new SystemSettingException(HttpStatus.BAD_REQUEST, "SETTING_READONLY", "设置项不可编辑");
            }
            if (group == null) {
                group = definition.group();
            } else if (!group.equals(definition.group())) {
                throw new SystemSettingException(HttpStatus.BAD_REQUEST, "SETTING_GROUP_MIXED", "一次只能保存同一分组的设置");
            }
            Object value = coerceAndValidate(definition, change.value());
            parsed.add(new ParsedChange(definition, value, change.expectedVersion()));
        }
        return parsed;
    }

    private Object coerceAndValidate(SystemSettingDefinition definition, Object raw) {
        if (raw == null) {
            throw new SystemSettingException(HttpStatus.BAD_REQUEST, "SETTING_INVALID", definition.labelZh() + "不能为空");
        }
        return switch (definition.type()) {
            case INTEGER, ENUM -> {
                if (definition.type() == SettingValueType.INTEGER
                        || definition.allowedValues().stream().anyMatch(item -> item instanceof Number)) {
                    int value = toInt(raw);
                    if (definition.min() != null && value < definition.min()) {
                        throw new SystemSettingException(HttpStatus.BAD_REQUEST, "SETTING_INVALID", definition.labelZh() + "过小");
                    }
                    if (definition.max() != null && value > definition.max()) {
                        throw new SystemSettingException(HttpStatus.BAD_REQUEST, "SETTING_INVALID", definition.labelZh() + "过大");
                    }
                    if (!definition.allowedValues().isEmpty()
                            && definition.allowedValues().stream().noneMatch(item -> toInt(item) == value)) {
                        throw new SystemSettingException(HttpStatus.BAD_REQUEST, "SETTING_INVALID", definition.labelZh() + "不在允许范围");
                    }
                    yield value;
                }
                String text = String.valueOf(raw).trim();
                if (!definition.allowedValues().isEmpty()
                        && definition.allowedValues().stream().noneMatch(item -> String.valueOf(item).equals(text))) {
                    throw new SystemSettingException(HttpStatus.BAD_REQUEST, "SETTING_INVALID", definition.labelZh() + "不在允许范围");
                }
                yield text;
            }
            case BOOLEAN -> {
                if (raw instanceof Boolean bool) {
                    yield bool;
                }
                yield Boolean.parseBoolean(String.valueOf(raw));
            }
            case STRING -> String.valueOf(raw).trim();
        };
    }

    private Object normalizeStored(SystemSettingDefinition definition, Object stored) {
        return coerceAndValidate(definition, stored);
    }

    private SystemSettingValidateResponse buildValidateResponse(List<ParsedChange> changes) {
        List<String> effects = new ArrayList<>();
        AuditRiskLevel highest = AuditRiskLevel.INFO;
        String confirmationTitle = "确认保存系统设置？";
        for (ParsedChange change : changes) {
            if (change.definition().riskLevel().ordinal() > highest.ordinal()) {
                highest = change.definition().riskLevel();
            }
            if (SystemSettingRegistry.REGISTRATION_MODE.equals(change.definition().key())) {
                RegistrationMode mode = RegistrationMode.valueOf(String.valueOf(change.value()));
                if (mode == RegistrationMode.CLOSED) {
                    effects.add("新用户将立即无法注册");
                    effects.add("注册用途短信验证码将停止发送");
                    confirmationTitle = "确认关闭用户注册？";
                    highest = AuditRiskLevel.HIGH;
                } else if (mode == RegistrationMode.INVITE_AND_SMS) {
                    effects.add("注册将同时要求短信验证码与有效邀请码");
                    confirmationTitle = "确认启用邀请码注册？";
                    highest = AuditRiskLevel.HIGH;
                } else {
                    effects.add("注册将恢复为短信验证模式");
                }
            }
            if (SystemSettingRegistry.AUDIT_RETENTION_DAYS.equals(change.definition().key())) {
                int current = toInt(effectiveValue(SystemSettingRegistry.AUDIT_RETENTION_DAYS));
                int next = toInt(change.value());
                if (next < current) {
                    effects.add("当前保留期限：" + current + " 天");
                    effects.add("新保留期限：" + next + " 天");
                    effects.add("下次清理后，超出新期限的历史日志可能被永久删除且不可恢复");
                    confirmationTitle = "确认缩短安全日志保留期限？";
                    highest = AuditRiskLevel.HIGH;
                } else {
                    effects.add("安全日志保留期限将调整为 " + next + " 天");
                }
            }
            if (SystemSettingRegistry.PASSWORD_MIN_LENGTH.equals(change.definition().key())) {
                effects.add("新注册与密码重置将要求至少 " + change.value() + " 位密码");
            }
            if (SystemSettingRegistry.USERNAME_COOLDOWN_DAYS.equals(change.definition().key())) {
                effects.add("用户名修改冷却期将调整为 " + change.value() + " 天");
            }
        }
        if (effects.isEmpty()) {
            effects.add("设置将立即生效");
        }
        return new SystemSettingValidateResponse(true, highest.name(), effects, confirmationTitle);
    }

    private static int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value).trim());
    }

    private record EffectiveValue(Object value, long version) {
    }

    private record ParsedChange(SystemSettingDefinition definition, Object value, Long expectedVersion) {
    }

    private record CacheEntry(Map<String, EffectiveValue> values, Instant expiresAt) {
    }
}
