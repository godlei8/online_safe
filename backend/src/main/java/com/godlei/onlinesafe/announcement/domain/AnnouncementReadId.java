package com.godlei.onlinesafe.announcement.domain;

import java.io.Serializable;
import java.util.Objects;

public class AnnouncementReadId implements Serializable {

    private String userId;
    private String announcementId;

    public AnnouncementReadId() {
    }

    public AnnouncementReadId(String userId, String announcementId) {
        this.userId = userId;
        this.announcementId = announcementId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AnnouncementReadId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(announcementId, that.announcementId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, announcementId);
    }
}
