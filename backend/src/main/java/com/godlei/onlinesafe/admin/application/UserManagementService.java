package com.godlei.onlinesafe.admin.application;

import com.godlei.onlinesafe.admin.infrastructure.UserSessionRepository;
import com.godlei.onlinesafe.admin.web.ManagedUserResponse;
import com.godlei.onlinesafe.admin.web.ManagedUserStatsResponse;
import com.godlei.onlinesafe.auth.domain.AppUser;
import com.godlei.onlinesafe.auth.domain.AppUserStatus;
import com.godlei.onlinesafe.auth.infrastructure.AppUserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserManagementService {

    private final AppUserRepository appUserRepository;
    private final UserSessionRepository userSessionRepository;
    private final PhoneMasker phoneMasker;
    private final Clock clock;

    public UserManagementService(
            AppUserRepository appUserRepository,
            UserSessionRepository userSessionRepository,
            PhoneMasker phoneMasker,
            Clock clock
    ) {
        this.appUserRepository = appUserRepository;
        this.userSessionRepository = userSessionRepository;
        this.phoneMasker = phoneMasker;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ManagedUserStatsResponse stats() {
        Instant since = clock.instant().minus(7, ChronoUnit.DAYS);
        return new ManagedUserStatsResponse(
                appUserRepository.count(),
                appUserRepository.countByStatus(AppUserStatus.ACTIVE),
                appUserRepository.countByStatus(AppUserStatus.DISABLED),
                appUserRepository.countActiveSince(since)
        );
    }

    @Transactional(readOnly = true)
    public Page<ManagedUserResponse> list(String q, AppUserStatus status, String registeredWithin, Pageable pageable) {
        Instant createdAfter = resolveCreatedAfter(registeredWithin);
        Specification<AppUser> spec = buildSpec(q, status, createdAfter);
        Page<AppUser> page = appUserRepository.findAll(spec, pageable);
        Map<String, Integer> sessionCounts = userSessionRepository.countByPrincipalNames(
                page.getContent().stream().map(AppUser::getUsername).collect(Collectors.toSet())
        );
        return page.map(user -> toResponse(user, sessionCounts.getOrDefault(user.getUsername(), 0)));
    }

    @Transactional
    public ManagedUserResponse disable(String userId) {
        AppUser user = requireUser(userId);
        user.disable();
        appUserRepository.save(user);
        userSessionRepository.deleteByPrincipalName(user.getUsername());
        return toResponse(user, 0);
    }

    @Transactional
    public ManagedUserResponse enable(String userId) {
        AppUser user = requireUser(userId);
        user.enable();
        appUserRepository.save(user);
        int sessions = userSessionRepository.countByPrincipalName(user.getUsername());
        return toResponse(user, sessions);
    }

    @Transactional
    public ManagedUserResponse revokeSessions(String userId) {
        AppUser user = requireUser(userId);
        userSessionRepository.deleteByPrincipalName(user.getUsername());
        return toResponse(user, 0);
    }

    private AppUser requireUser(String userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("用户不存在"));
    }

    private Instant resolveCreatedAfter(String registeredWithin) {
        if (registeredWithin == null || registeredWithin.isBlank()) {
            return null;
        }
        return switch (registeredWithin.trim().toLowerCase(Locale.ROOT)) {
            case "7d" -> clock.instant().minus(7, ChronoUnit.DAYS);
            case "30d" -> clock.instant().minus(30, ChronoUnit.DAYS);
            case "90d" -> clock.instant().minus(90, ChronoUnit.DAYS);
            default -> throw new InvalidUserOperationException(
                    "INVALID_REGISTERED_WITHIN",
                    "注册时间筛选仅支持 7d、30d、90d"
            );
        };
    }

    private Specification<AppUser> buildSpec(String q, AppUserStatus status, Instant createdAfter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (createdAfter != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdAfter));
            }
            if (q != null && !q.isBlank()) {
                String pattern = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("username")), pattern),
                        cb.like(cb.lower(root.get("normalizedUsername")), pattern),
                        cb.like(cb.lower(root.get("phone")), pattern)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private ManagedUserResponse toResponse(AppUser user, int activeSessionCount) {
        return new ManagedUserResponse(
                user.getId(),
                user.getUsername(),
                phoneMasker.mask(user.getPhone()),
                user.getStatus(),
                user.getCreatedAt(),
                user.getLastLoginAt(),
                activeSessionCount,
                null,
                null
        );
    }
}
