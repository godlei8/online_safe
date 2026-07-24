package com.godlei.onlinesafe.announcement.application;

import com.godlei.onlinesafe.announcement.domain.Announcement;
import com.godlei.onlinesafe.announcement.domain.AnnouncementRead;
import com.godlei.onlinesafe.announcement.domain.AnnouncementStatus;
import com.godlei.onlinesafe.announcement.infrastructure.AnnouncementReadRepository;
import com.godlei.onlinesafe.announcement.infrastructure.AnnouncementRepository;
import com.godlei.onlinesafe.announcement.web.AnnouncementAdminResponse;
import com.godlei.onlinesafe.announcement.web.AnnouncementInboxResponse;
import com.godlei.onlinesafe.announcement.web.AnnouncementUpsertRequest;
import com.godlei.onlinesafe.announcement.web.AnnouncementUserResponse;
import com.godlei.onlinesafe.audit.application.SecurityAuditService;
import com.godlei.onlinesafe.audit.domain.AuditEventType;
import com.godlei.onlinesafe.audit.domain.AuditResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementReadRepository announcementReadRepository;
    private final SecurityAuditService securityAuditService;

    public AnnouncementService(
            AnnouncementRepository announcementRepository,
            AnnouncementReadRepository announcementReadRepository,
            SecurityAuditService securityAuditService
    ) {
        this.announcementRepository = announcementRepository;
        this.announcementReadRepository = announcementReadRepository;
        this.securityAuditService = securityAuditService;
    }

    @Transactional(readOnly = true)
    public Page<AnnouncementAdminResponse> listAdmin(AnnouncementStatus status, Pageable pageable) {
        Page<Announcement> page = status == null
                ? announcementRepository.findAll(pageable)
                : announcementRepository.findByStatus(status, pageable);
        return page.map(AnnouncementAdminResponse::from);
    }

    @Transactional
    public AnnouncementAdminResponse create(String adminId, AnnouncementUpsertRequest request) {
        validateWindow(request.startsAt(), request.endsAt());
        Announcement announcement = Announcement.create(
                request.title().trim(),
                request.body().trim(),
                request.pinned(),
                request.startsAt(),
                request.endsAt(),
                adminId
        );
        Announcement saved = announcementRepository.save(announcement);
        auditAnnouncement(AuditEventType.ANNOUNCEMENT_CREATED, saved);
        return AnnouncementAdminResponse.from(saved);
    }

    @Transactional
    public AnnouncementAdminResponse update(String id, AnnouncementUpsertRequest request) {
        validateWindow(request.startsAt(), request.endsAt());
        Announcement announcement = requireAnnouncement(id);
        announcement.update(
                request.title().trim(),
                request.body().trim(),
                request.pinned(),
                request.startsAt(),
                request.endsAt()
        );
        Announcement saved = announcementRepository.save(announcement);
        auditAnnouncement(AuditEventType.ANNOUNCEMENT_UPDATED, saved);
        return AnnouncementAdminResponse.from(saved);
    }

    @Transactional
    public AnnouncementAdminResponse publish(String id) {
        Announcement announcement = requireAnnouncement(id);
        if (announcement.getStatus() == AnnouncementStatus.PUBLISHED) {
            throw new InvalidAnnouncementOperationException("ANNOUNCEMENT_ALREADY_PUBLISHED", "公告已发布");
        }
        announcement.publish(Instant.now());
        // 重新发布视为新一轮触达：清除已读，用户端按最新 publishedAt 强制弹窗
        announcementReadRepository.deleteByAnnouncementId(id);
        Announcement saved = announcementRepository.save(announcement);
        auditAnnouncement(AuditEventType.ANNOUNCEMENT_PUBLISHED, saved);
        return AnnouncementAdminResponse.from(saved);
    }

    @Transactional
    public AnnouncementAdminResponse offline(String id) {
        Announcement announcement = requireAnnouncement(id);
        if (announcement.getStatus() != AnnouncementStatus.PUBLISHED) {
            throw new InvalidAnnouncementOperationException("ANNOUNCEMENT_NOT_PUBLISHED", "仅已发布公告可下线");
        }
        announcement.offline();
        Announcement saved = announcementRepository.save(announcement);
        auditAnnouncement(AuditEventType.ANNOUNCEMENT_OFFLINED, saved);
        return AnnouncementAdminResponse.from(saved);
    }

    private void auditAnnouncement(AuditEventType type, Announcement announcement) {
        SecurityAuditService.ActorSnapshot actor = securityAuditService.requireAdminActor();
        securityAuditService.recordInTx(
                type,
                AuditResult.SUCCESS,
                actor.type(),
                actor.id(),
                actor.label(),
                "ANNOUNCEMENT",
                announcement.getId(),
                announcement.getTitle(),
                null,
                Map.of("title", announcement.getTitle())
        );
    }

    @Transactional(readOnly = true)
    public List<AnnouncementUserResponse> listForUser(String userId) {
        Instant now = Instant.now();
        List<Announcement> visible = announcementRepository.findVisible(now);
        Set<String> readIds = readIdsFor(userId, visible);
        return visible.stream()
                .map(item -> toUserResponse(item, readIds.contains(item.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public AnnouncementInboxResponse inbox(String userId) {
        Instant now = Instant.now();
        List<Announcement> visible = announcementRepository.findVisible(now);
        Set<String> readIds = readIdsFor(userId, visible);

        List<Announcement> unread = visible.stream()
                .filter(item -> !readIds.contains(item.getId()))
                .toList();

        AnnouncementUserResponse latestUnread = unread.isEmpty()
                ? null
                : toUserResponse(unread.getFirst(), false);

        AnnouncementUserResponse pinned = visible.stream()
                .filter(Announcement::isPinned)
                .findFirst()
                .map(item -> toUserResponse(item, readIds.contains(item.getId())))
                .orElse(null);

        return new AnnouncementInboxResponse(unread.size(), latestUnread, pinned);
    }

    @Transactional
    public void markRead(String userId, String announcementId) {
        Announcement announcement = requireAnnouncement(announcementId);
        if (!announcement.isVisibleAt(Instant.now())) {
            throw new AnnouncementNotFoundException();
        }
        if (announcementReadRepository.existsByUserIdAndAnnouncementId(userId, announcementId)) {
            return;
        }
        announcementReadRepository.save(AnnouncementRead.mark(userId, announcementId, Instant.now()));
    }

    private Set<String> readIdsFor(String userId, List<Announcement> visible) {
        if (visible.isEmpty()) {
            return Set.of();
        }
        List<String> ids = visible.stream().map(Announcement::getId).toList();
        return announcementReadRepository.findByUserIdAndAnnouncementIdIn(userId, ids).stream()
                .map(AnnouncementRead::getAnnouncementId)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private AnnouncementUserResponse toUserResponse(Announcement announcement, boolean read) {
        return new AnnouncementUserResponse(
                announcement.getId(),
                announcement.getTitle(),
                announcement.getBody(),
                announcement.isPinned(),
                announcement.getStartsAt(),
                announcement.getEndsAt(),
                announcement.getPublishedAt(),
                read
        );
    }

    private Announcement requireAnnouncement(String id) {
        return announcementRepository.findById(id).orElseThrow(AnnouncementNotFoundException::new);
    }

    private void validateWindow(Instant startsAt, Instant endsAt) {
        if (startsAt != null && endsAt != null && endsAt.isBefore(startsAt)) {
            throw new InvalidAnnouncementOperationException(
                    "ANNOUNCEMENT_INVALID_WINDOW",
                    "结束时间不能早于开始时间"
            );
        }
    }
}
