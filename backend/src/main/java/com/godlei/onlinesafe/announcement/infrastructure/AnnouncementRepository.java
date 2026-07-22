package com.godlei.onlinesafe.announcement.infrastructure;

import com.godlei.onlinesafe.announcement.domain.Announcement;
import com.godlei.onlinesafe.announcement.domain.AnnouncementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, String> {

    Page<Announcement> findByStatus(AnnouncementStatus status, Pageable pageable);

    @Query("""
            select a from Announcement a
            where a.status = com.godlei.onlinesafe.announcement.domain.AnnouncementStatus.PUBLISHED
              and (a.startsAt is null or a.startsAt <= :now)
              and (a.endsAt is null or a.endsAt >= :now)
            order by a.publishedAt desc, a.createdAt desc
            """)
    List<Announcement> findVisible(@Param("now") Instant now);
}
