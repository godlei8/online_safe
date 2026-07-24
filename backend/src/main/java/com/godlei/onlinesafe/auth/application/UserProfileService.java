package com.godlei.onlinesafe.auth.application;

import com.godlei.onlinesafe.audit.application.IdentifierMasker;
import com.godlei.onlinesafe.audit.application.SecurityAuditService;
import com.godlei.onlinesafe.audit.domain.AuditActorType;
import com.godlei.onlinesafe.audit.domain.AuditEventType;
import com.godlei.onlinesafe.audit.domain.AuditResult;
import com.godlei.onlinesafe.auth.domain.AppUser;
import com.godlei.onlinesafe.auth.infrastructure.AppUserRepository;
import com.godlei.onlinesafe.auth.web.ProfileResponse;
import com.godlei.onlinesafe.cos.AvatarObjectStorage;
import com.godlei.onlinesafe.cos.CosProperties;
import com.godlei.onlinesafe.security.AppUserPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class UserProfileService {

    private static final Duration USERNAME_COOLDOWN = Duration.ofDays(30);
    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final AppUserRepository userRepository;
    private final UsernameNormalizer usernameNormalizer;
    private final AvatarObjectStorage avatarObjectStorage;
    private final CosProperties cosProperties;
    private final SecurityContextRepository securityContextRepository;
    private final SecurityAuditService securityAuditService;
    private final IdentifierMasker identifierMasker;
    private final Clock clock;

    public UserProfileService(
            AppUserRepository userRepository,
            UsernameNormalizer usernameNormalizer,
            AvatarObjectStorage avatarObjectStorage,
            CosProperties cosProperties,
            SecurityContextRepository securityContextRepository,
            SecurityAuditService securityAuditService,
            IdentifierMasker identifierMasker,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.usernameNormalizer = usernameNormalizer;
        this.avatarObjectStorage = avatarObjectStorage;
        this.cosProperties = cosProperties;
        this.securityContextRepository = securityContextRepository;
        this.securityAuditService = securityAuditService;
        this.identifierMasker = identifierMasker;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(String userId) {
        AppUser user = requireUser(userId);
        return toResponse(user);
    }

    @Transactional
    public ProfileResponse changeUsername(
            String userId,
            String rawUsername,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        AppUser user = requireUser(userId);
        UsernameNormalizer.UsernameValue username = usernameNormalizer.normalizeForRegistration(rawUsername);
        if (username.normalized().equals(user.getNormalizedUsername())) {
            return toResponse(user);
        }

        Instant now = clock.instant();
        if (user.getUsernameChangedAt() != null
                && user.getUsernameChangedAt().plus(USERNAME_COOLDOWN).isAfter(now)) {
            throw new ProfileException("USERNAME_CHANGE_COOLDOWN", "30 天内仅可修改一次用户名");
        }
        if (userRepository.existsByNormalizedUsernameAndIdNot(username.normalized(), userId)) {
            throw new ProfileException("USERNAME_ALREADY_EXISTS", "该用户名已被占用");
        }

        String previousHint = identifierMasker.maskUsername(user.getUsername());
        String newHint = identifierMasker.maskUsername(username.display());
        user.changeUsername(username.display(), username.normalized(), now);
        userRepository.save(user);
        securityAuditService.recordInTx(
                AuditEventType.USERNAME_CHANGED,
                AuditResult.SUCCESS,
                AuditActorType.USER,
                user.getId(),
                user.getUsername(),
                "USER",
                user.getId(),
                user.getUsername(),
                null,
                Map.of("previousUsernameHint", previousHint, "newUsernameHint", newHint)
        );
        refreshPrincipal(user, request, response);
        return toResponse(user);
    }

    @Transactional
    public ProfileResponse uploadAvatar(String userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ProfileException("AVATAR_INVALID", "请选择要上传的头像图片");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new ProfileException("AVATAR_TOO_LARGE", "头像不能超过 2MB");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ProfileException("AVATAR_INVALID", "仅支持 JPEG、PNG 或 WebP 图片");
        }
        if ("tencent".equalsIgnoreCase(cosProperties.provider())
                && (cosProperties.secretId().isBlank() || cosProperties.bucket().isBlank())) {
            throw new ProfileException("COS_NOT_CONFIGURED", "对象存储未正确配置");
        }

        AppUser user = requireUser(userId);
        String ext = extensionFor(contentType);
        String objectKey = cosProperties.objectDirPrefix() + "/" + userId + "/" + UUID.randomUUID() + ext;
        try (InputStream inputStream = file.getInputStream()) {
            String url = avatarObjectStorage.upload(objectKey, inputStream, file.getSize(), contentType);
            user.updateAvatarUrl(url);
            userRepository.save(user);
            securityAuditService.recordInTx(
                    AuditEventType.USER_AVATAR_CHANGED,
                    AuditResult.SUCCESS,
                    AuditActorType.USER,
                    user.getId(),
                    user.getUsername(),
                    "USER",
                    user.getId(),
                    user.getUsername(),
                    null,
                    null
            );
            return toResponse(user);
        } catch (IOException exception) {
            throw new ProfileException("COS_UPLOAD_FAILED", "头像上传失败，请稍后再试");
        }
    }

    private void refreshPrincipal(AppUser user, HttpServletRequest request, HttpServletResponse response) {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current == null || !(current.getPrincipal() instanceof AppUserPrincipal old)) {
            return;
        }
        AppUserPrincipal next = new AppUserPrincipal(
                user.getId(),
                user.getUsername(),
                old.passwordHash(),
                old.enabled()
        );
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                next,
                null,
                next.getAuthorities()
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private AppUser requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ProfileException("USER_NOT_FOUND", "用户不存在"));
    }

    private ProfileResponse toResponse(AppUser user) {
        Instant now = clock.instant();
        Instant nextChangeAt = null;
        boolean canChangeUsername = true;
        if (user.getUsernameChangedAt() != null) {
            Instant cooldownEnd = user.getUsernameChangedAt().plus(USERNAME_COOLDOWN);
            if (cooldownEnd.isAfter(now)) {
                canChangeUsername = false;
                nextChangeAt = cooldownEnd;
            }
        }
        return new ProfileResponse(
                user.getId(),
                user.getUsername(),
                maskPhone(user.getPhone()),
                user.getAvatarUrl(),
                canChangeUsername,
                nextChangeAt
        );
    }

    static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "****";
        }
        // +8613812345678 / 13812345678
        String digits = phone.startsWith("+86") ? phone.substring(3) : phone;
        if (digits.length() < 7) {
            return "****";
        }
        return digits.substring(0, 3) + "****" + digits.substring(digits.length() - 4);
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
