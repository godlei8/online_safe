package com.godlei.onlinesafe.announcement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "announcement_read")
@IdClass(AnnouncementReadId.class)
public class AnnouncementRead {

    @Id
    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Id
    @Column(name = "announcement_id", nullable = false, length = 36)
    private String announcementId;

    @Column(name = "read_at", nullable = false)
    private Instant readAt;

    protected AnnouncementRead() {
    }

    private AnnouncementRead(String userId, String announcementId, Instant readAt) {
        this.userId = Objects.requireNonNull(userId);
        this.announcementId = Objects.requireNonNull(announcementId);
        this.readAt = Objects.requireNonNull(readAt);
    }

    public static AnnouncementRead mark(String userId, String announcementId, Instant readAt) {
        return new AnnouncementRead(userId, announcementId, readAt);
    }

    public String getUserId() {
        return userId;
    }

    public String getAnnouncementId() {
        return announcementId;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
