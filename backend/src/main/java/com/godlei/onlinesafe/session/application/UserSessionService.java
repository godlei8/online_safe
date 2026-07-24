package com.godlei.onlinesafe.session.application;

import com.godlei.onlinesafe.session.domain.SessionMetadataKeys;
import com.godlei.onlinesafe.session.domain.UserSessionView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class UserSessionService {

    private static final Logger log = LoggerFactory.getLogger(UserSessionService.class);

    private final FindByIndexNameSessionRepository<Session> indexedSessionRepository;
    private final SessionRegistry sessionRegistry;
    private final SessionMetadataService sessionMetadataService;
    private final SessionLimitService sessionLimitService;

    @SuppressWarnings("unchecked")
    public UserSessionService(
            SessionRepository<? extends Session> sessionRepository,
            SessionRegistry sessionRegistry,
            SessionMetadataService sessionMetadataService,
            SessionLimitService sessionLimitService
    ) {
        if (!(sessionRepository instanceof FindByIndexNameSessionRepository<?> indexed)) {
            throw new IllegalStateException("会话管理需要支持按主体索引的 SessionRepository");
        }
        this.indexedSessionRepository = (FindByIndexNameSessionRepository<Session>) indexed;
        this.sessionRegistry = sessionRegistry;
        this.sessionMetadataService = sessionMetadataService;
        this.sessionLimitService = sessionLimitService;
    }

    public int maxActiveSessions() {
        return sessionLimitService.maxActiveUserSessions();
    }

    public int countActiveByPrincipal(String principalName) {
        return listActiveSessions(principalName, null).size();
    }

    public List<UserSessionView> listActiveForUser(String principalName, String currentInternalSessionId) {
        List<UserSessionView> views = listActiveSessions(principalName, currentInternalSessionId);
        views.sort(Comparator
                .comparing(UserSessionView::current).reversed()
                .thenComparing(UserSessionView::lastActiveAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return views;
    }

    /** @return true 表示实际删除了一个会话 */
    public boolean revokeOtherByPublicId(String principalName, String currentInternalSessionId, String publicSessionId) {
        List<UserSessionView> sessions = listActiveSessions(principalName, currentInternalSessionId);
        UserSessionView target = sessions.stream()
                .filter(item -> Objects.equals(item.publicId(), publicSessionId))
                .findFirst()
                .orElse(null);
        if (target == null) {
            return false;
        }
        if (target.current()) {
            throw new SessionException(
                    HttpStatus.CONFLICT,
                    "CURRENT_SESSION_USE_LOGOUT",
                    "请使用退出登录结束当前设备会话"
            );
        }
        deleteInternalSession(target.internalSessionId());
        return true;
    }

    public int revokeOthers(String principalName, String currentInternalSessionId) {
        int revoked = 0;
        for (UserSessionView session : listActiveSessions(principalName, currentInternalSessionId)) {
            if (!session.current()) {
                deleteInternalSession(session.internalSessionId());
                revoked++;
            }
        }
        return revoked;
    }

    public int revokeAll(String principalName) {
        List<UserSessionView> sessions = listActiveSessions(principalName, null);
        for (UserSessionView session : sessions) {
            deleteInternalSession(session.internalSessionId());
        }
        return sessions.size();
    }

    public int deleteAllByPrincipal(String principalName) {
        return revokeAll(principalName);
    }

    public int revokeOthersKeepCurrent(String oldPrincipalName, String currentInternalSessionId) {
        return revokeOthers(oldPrincipalName, currentInternalSessionId);
    }

    /** 用户名变更后，将当前会话的主体索引切换到新用户名。 */
    public void rebindPrincipal(String internalSessionId, String newPrincipalName) {
        if (internalSessionId == null || newPrincipalName == null || newPrincipalName.isBlank()) {
            return;
        }
        Session session = indexedSessionRepository.findById(internalSessionId);
        if (session == null) {
            return;
        }
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, newPrincipalName);
        indexedSessionRepository.save(session);
    }

    private List<UserSessionView> listActiveSessions(String principalName, String currentInternalSessionId) {
        if (principalName == null || principalName.isBlank()) {
            return List.of();
        }
        try {
            Map<String, ? extends Session> sessions = indexedSessionRepository.findByIndexNameAndIndexValue(
                    FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                    principalName
            );
            List<UserSessionView> result = new ArrayList<>();
            for (Session session : sessions.values()) {
                if (session == null || isExpired(session) || isMarkedExpired(session.getId())) {
                    continue;
                }
                result.add(toView(session, currentInternalSessionId));
            }
            return result;
        } catch (RuntimeException exception) {
            log.error("列举用户会话失败");
            throw new SessionException(HttpStatus.SERVICE_UNAVAILABLE, "SESSION_LIST_UNAVAILABLE", "暂时无法加载登录设备列表");
        }
    }

    private UserSessionView toView(Session session, String currentInternalSessionId) {
        String publicId = sessionMetadataService.ensurePublicId(session);
        String browser = sessionMetadataService.readString(session, SessionMetadataKeys.BROWSER_FAMILY, "未知浏览器");
        String os = sessionMetadataService.readString(session, SessionMetadataKeys.OS_FAMILY, "未知系统");
        String deviceType = sessionMetadataService.readString(session, SessionMetadataKeys.DEVICE_TYPE, "UNKNOWN");
        String ipMasked = sessionMetadataService.readString(session, SessionMetadataKeys.IP_MASKED, "unknown");
        Instant loginAt = sessionMetadataService.readInstant(session, SessionMetadataKeys.LOGIN_AT);
        if (loginAt == null) {
            loginAt = session.getCreationTime();
        }
        Instant lastActiveAt = session.getLastAccessedTime();
        Instant expiresAt = lastActiveAt.plus(session.getMaxInactiveInterval());
        boolean current = currentInternalSessionId != null && currentInternalSessionId.equals(session.getId());
        String displayName = browser + " · " + os;
        indexedSessionRepository.save(session);
        return new UserSessionView(
                publicId,
                session.getId(),
                current,
                deviceType,
                browser,
                os,
                displayName,
                ipMasked,
                loginAt,
                lastActiveAt,
                expiresAt
        );
    }

    private void deleteInternalSession(String internalSessionId) {
        if (internalSessionId == null || internalSessionId.isBlank()) {
            return;
        }
        try {
            SessionInformation information = sessionRegistry.getSessionInformation(internalSessionId);
            if (information != null) {
                information.expireNow();
            }
        } catch (RuntimeException ignored) {
            // registry 缺失时仍删除底层会话
        }
        indexedSessionRepository.deleteById(internalSessionId);
    }

    private boolean isExpired(Session session) {
        Instant last = session.getLastAccessedTime();
        Duration maxInactive = session.getMaxInactiveInterval();
        if (last == null || maxInactive == null) {
            return false;
        }
        return last.plus(maxInactive).isBefore(Instant.now());
    }

    private boolean isMarkedExpired(String sessionId) {
        try {
            SessionInformation information = sessionRegistry.getSessionInformation(sessionId);
            return information != null && information.isExpired();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

}
