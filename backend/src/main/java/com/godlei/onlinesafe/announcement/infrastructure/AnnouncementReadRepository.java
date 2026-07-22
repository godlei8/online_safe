package com.godlei.onlinesafe.announcement.infrastructure;

import com.godlei.onlinesafe.announcement.domain.AnnouncementRead;
import com.godlei.onlinesafe.announcement.domain.AnnouncementReadId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AnnouncementReadRepository extends JpaRepository<AnnouncementRead, AnnouncementReadId> {

    List<AnnouncementRead> findByUserIdAndAnnouncementIdIn(String userId, Collection<String> announcementIds);

    boolean existsByUserIdAndAnnouncementId(String userId, String announcementId);

    void deleteByAnnouncementId(String announcementId);
}
