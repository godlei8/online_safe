package com.godlei.onlinesafe.settings.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "system_setting")
public class SystemSetting {

    @Id
    @Column(name = "setting_key", nullable = false, length = 96)
    private String settingKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "value_json", nullable = false, columnDefinition = "json")
    private Object valueJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 16)
    private SettingValueType valueType;

    @Column(name = "updated_by_admin_id", nullable = false, length = 36)
    private String updatedByAdminId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected SystemSetting() {
    }

    public static SystemSetting create(
            String key,
            Object value,
            SettingValueType type,
            String adminId,
            Instant now
    ) {
        SystemSetting setting = new SystemSetting();
        setting.settingKey = key;
        setting.valueJson = value;
        setting.valueType = type;
        setting.updatedByAdminId = adminId;
        setting.updatedAt = now;
        return setting;
    }

    public void updateValue(Object value, String adminId, Instant now) {
        this.valueJson = value;
        this.updatedByAdminId = adminId;
        this.updatedAt = now;
    }

    public String getSettingKey() {
        return settingKey;
    }

    public Object getValueJson() {
        return valueJson;
    }

    public SettingValueType getValueType() {
        return valueType;
    }

    public String getUpdatedByAdminId() {
        return updatedByAdminId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
