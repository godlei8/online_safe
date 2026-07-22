package com.godlei.onlinesafe.auth.application;

import com.godlei.onlinesafe.admin.infrastructure.UserSessionRepository;
import com.godlei.onlinesafe.auth.domain.AppUser;
import com.godlei.onlinesafe.auth.domain.UserSecurityQuestion;
import com.godlei.onlinesafe.auth.infrastructure.AppUserRepository;
import com.godlei.onlinesafe.auth.web.PasswordResetConfirmRequest;
import com.godlei.onlinesafe.auth.web.PasswordResetLookupResponse;
import com.godlei.onlinesafe.auth.web.PasswordResetQuestionResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@Service
public class PasswordResetService {

    private static final String UNAVAILABLE_MESSAGE = "无法通过密保重置密码，请检查账号后重试";
    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    private final AppUserRepository userRepository;
    private final SecurityQuestionService securityQuestionService;
    private final PasswordEncoder passwordEncoder;
    private final PhoneNormalizer phoneNormalizer;
    private final UsernameNormalizer usernameNormalizer;
    private final UserSessionRepository userSessionRepository;
    private final PasswordResetRateLimiter rateLimiter;

    public PasswordResetService(
            AppUserRepository userRepository,
            SecurityQuestionService securityQuestionService,
            PasswordEncoder passwordEncoder,
            PhoneNormalizer phoneNormalizer,
            UsernameNormalizer usernameNormalizer,
            UserSessionRepository userSessionRepository,
            PasswordResetRateLimiter rateLimiter
    ) {
        this.userRepository = userRepository;
        this.securityQuestionService = securityQuestionService;
        this.passwordEncoder = passwordEncoder;
        this.phoneNormalizer = phoneNormalizer;
        this.usernameNormalizer = usernameNormalizer;
        this.userSessionRepository = userSessionRepository;
        this.rateLimiter = rateLimiter;
    }

    @Transactional(readOnly = true)
    public PasswordResetLookupResponse lookup(String identifier, String clientKey) {
        rateLimiter.check("lookup:" + clientKey);
        AppUser user = findUser(identifier)
                .orElseThrow(() -> new PasswordResetException("PASSWORD_RESET_UNAVAILABLE", UNAVAILABLE_MESSAGE));
        List<UserSecurityQuestion> questions = securityQuestionService.listByOwner(user.getId());
        if (questions.isEmpty()) {
            throw new PasswordResetException("PASSWORD_RESET_UNAVAILABLE", UNAVAILABLE_MESSAGE);
        }
        return new PasswordResetLookupResponse(questions.stream()
                .map(q -> new PasswordResetQuestionResponse(
                        q.getId(),
                        q.getQuestionType().name(),
                        q.getQuestionCode(),
                        q.getQuestionText(),
                        q.getSortOrder()
                ))
                .toList());
    }

    @Transactional
    public void confirm(PasswordResetConfirmRequest request, String clientKey) {
        rateLimiter.check("confirm:" + clientKey + ":" + normalizeRateKey(request.identifier()));

        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new PasswordResetException("PASSWORD_CONFIRMATION_MISMATCH", "两次输入的密码不一致");
        }
        if (request.newPassword().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new PasswordResetException("PASSWORD_TOO_LONG", "密码内容过长");
        }

        AppUser user = findUser(request.identifier())
                .orElseThrow(() -> new PasswordResetException("PASSWORD_RESET_FAILED", UNAVAILABLE_MESSAGE));
        List<UserSecurityQuestion> questions = securityQuestionService.listByOwner(user.getId());
        if (questions.isEmpty() || !securityQuestionService.verifyAll(questions, request.answers())) {
            throw new PasswordResetException("PASSWORD_RESET_FAILED", "密保答案不正确，请重试");
        }

        user.changePassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        // 登录密码与保险箱加解密无关：仅吊销会话，保留账密记录
        userSessionRepository.deleteByPrincipalName(user.getUsername());
        rateLimiter.clear("confirm:" + clientKey + ":" + normalizeRateKey(request.identifier()));
        rateLimiter.clear("lookup:" + clientKey);
    }

    private Optional<AppUser> findUser(String identifier) {
        try {
            return userRepository.findByPhone(phoneNormalizer.normalize(identifier));
        } catch (InvalidRegistrationException ignored) {
            String username = usernameNormalizer.normalizeForLogin(identifier);
            if (username.isBlank()) {
                return Optional.empty();
            }
            return userRepository.findByNormalizedUsername(username);
        }
    }

    private String normalizeRateKey(String identifier) {
        return identifier == null ? "" : identifier.trim().toLowerCase();
    }
}
