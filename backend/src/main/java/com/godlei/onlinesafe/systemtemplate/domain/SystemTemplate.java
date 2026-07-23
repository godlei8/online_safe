package com.godlei.onlinesafe.systemtemplate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "system_template")
public class SystemTemplate {

    @Id
    @Column(name = "id", nullable = false, length = 36, updatable = false)
    private String id;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "platform", nullable = false, length = 128)
    private String platform;

    @Column(name = "channel", nullable = false, length = 128)
    private String channel;

    @Column(name = "channel_url", nullable = false, length = 512)
    private String channelUrl;

    @Column(name = "payload_json", nullable = false, columnDefinition = "LONGTEXT")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SystemTemplateStatus status;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_by_admin_id", nullable = false, length = 36, updatable = false)
    private String createdByAdminId;

    @Column(name = "updated_by_admin_id", nullable = false, length = 36)
    private String updatedByAdminId;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected SystemTemplate() {
    }

    private SystemTemplate(
            String name,
            String platform,
            String channel,
            String channelUrl,
            String payloadJson,
            int sortOrder,
            String adminId
    ) {
        this.id = UUID.randomUUID().toString();
        this.name = Objects.requireNonNull(name);
        this.platform = Objects.requireNonNull(platform);
        this.channel = channel == null ? "" : channel;
        this.channelUrl = channelUrl == null ? "" : channelUrl;
        this.payloadJson = Objects.requireNonNull(payloadJson);
        this.status = SystemTemplateStatus.DRAFT;
        this.sortOrder = sortOrder;
        this.createdByAdminId = Objects.requireNonNull(adminId);
        this.updatedByAdminId = adminId;
    }

    public static SystemTemplate create(
            String name,
            String platform,
            String channel,
            String channelUrl,
            String payloadJson,
            int sortOrder,
            String adminId
    ) {
        return new SystemTemplate(name, platform, channel, channelUrl, payloadJson, sortOrder, adminId);
    }

    public void updateContent(
            String name,
            String platform,
            String channel,
            String channelUrl,
            String payloadJson,
            Integer sortOrder,
            String adminId
    ) {
        this.name = Objects.requireNonNull(name);
        this.platform = Objects.requireNonNull(platform);
        this.channel = channel == null ? "" : channel;
        this.channelUrl = channelUrl == null ? "" : channelUrl;
        this.payloadJson = Objects.requireNonNull(payloadJson);
        if (sortOrder != null) {
            this.sortOrder = sortOrder;
        }
        this.updatedByAdminId = Objects.requireNonNull(adminId);
        // 已发布/已下线修改后回到草稿，需重新发布才对用户可见
        if (status != SystemTemplateStatus.DRAFT) {
            this.status = SystemTemplateStatus.DRAFT;
            this.publishedAt = null;
        }
    }

    public void publish(Instant now) {
        this.status = SystemTemplateStatus.PUBLISHED;
        this.publishedAt = Objects.requireNonNull(now);
    }

    public void offline() {
        this.status = SystemTemplateStatus.OFFLINE;
    }

    public void updateSortOrder(int sortOrder, String adminId) {
        this.sortOrder = sortOrder;
        this.updatedByAdminId = Objects.requireNonNull(adminId);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPlatform() {
        return platform;
    }

    public String getChannel() {
        return channel;
    }

    public String getChannelUrl() {
        return channelUrl;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public SystemTemplateStatus getStatus() {
        return status;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public String getCreatedByAdminId() {
        return createdByAdminId;
    }

    public String getUpdatedByAdminId() {
        return updatedByAdminId;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
