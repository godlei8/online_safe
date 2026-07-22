package com.godlei.onlinesafe.announcement.domain;

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
@Table(name = "announcement")
public class Announcement {

    @Id
    @Column(name = "id", nullable = false, length = 36, updatable = false)
    private String id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AnnouncementStatus status;

    @Column(name = "pinned", nullable = false)
    private boolean pinned;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_by_admin_id", nullable = false, length = 36, updatable = false)
    private String createdByAdminId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Announcement() {
    }

    private Announcement(
            String title,
            String body,
            boolean pinned,
            Instant startsAt,
            Instant endsAt,
            String createdByAdminId
    ) {
        this.id = UUID.randomUUID().toString();
        this.title = Objects.requireNonNull(title);
        this.body = Objects.requireNonNull(body);
        this.status = AnnouncementStatus.DRAFT;
        this.pinned = pinned;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.createdByAdminId = Objects.requireNonNull(createdByAdminId);
    }

    public static Announcement create(
            String title,
            String body,
            boolean pinned,
            Instant startsAt,
            Instant endsAt,
            String createdByAdminId
    ) {
        return new Announcement(title, body, pinned, startsAt, endsAt, createdByAdminId);
    }

    public void update(String title, String body, boolean pinned, Instant startsAt, Instant endsAt) {
        this.title = Objects.requireNonNull(title);
        this.body = Objects.requireNonNull(body);
        this.pinned = pinned;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        // 已发布/已下线的公告修改后回到草稿，需重新发布才对用户生效
        if (status != AnnouncementStatus.DRAFT) {
            this.status = AnnouncementStatus.DRAFT;
            this.publishedAt = null;
        }
    }

    public void publish(Instant now) {
        this.status = AnnouncementStatus.PUBLISHED;
        this.publishedAt = Objects.requireNonNull(now);
    }

    public void offline() {
        this.status = AnnouncementStatus.OFFLINE;
    }

    public boolean isVisibleAt(Instant now) {
        if (status != AnnouncementStatus.PUBLISHED) {
            return false;
        }
        if (startsAt != null && now.isBefore(startsAt)) {
            return false;
        }
        if (endsAt != null && now.isAfter(endsAt)) {
            return false;
        }
        return true;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public AnnouncementStatus getStatus() {
        return status;
    }

    public boolean isPinned() {
        return pinned;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public String getCreatedByAdminId() {
        return createdByAdminId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
