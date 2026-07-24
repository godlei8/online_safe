package com.godlei.onlinesafe.auth.application;

import com.godlei.onlinesafe.audit.application.SecurityAuditService;
import com.godlei.onlinesafe.auth.domain.AppUser;
import com.godlei.onlinesafe.auth.domain.SmsPurpose;
import com.godlei.onlinesafe.auth.infrastructure.AppUserRepository;
import com.godlei.onlinesafe.auth.web.PasswordResetConfirmRequest;
import com.godlei.onlinesafe.session.application.UserSessionService;
import com.godlei.onlinesafe.settings.application.SystemSettingService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Service
public class PasswordResetService {

    private static final String FAILED_MESSAGE = "验证码不正确或已失效，请重试";
    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    private final AppUserRepository userRepository;
    private final SmsVerificationService smsVerificationService;
    private final PasswordEncoder passwordEncoder;
    private final PhoneNormalizer phoneNormalizer;
    private final UserSessionService userSessionService;
    private final PasswordResetRateLimiter rateLimiter;
    private final SecurityAuditService securityAuditService;
    private final SystemSettingService systemSettingService;

    public PasswordResetService(
            AppUserRepository userRepository,
            SmsVerificationService smsVerificationService,
            PasswordEncoder passwordEncoder,
            PhoneNormalizer phoneNormalizer,
            UserSessionService userSessionService,
            PasswordResetRateLimiter rateLimiter,
            SecurityAuditService securityAuditService,
            SystemSettingService systemSettingService
    ) {
        this.userRepository = userRepository;
        this.smsVerificationService = smsVerificationService;
        this.passwordEncoder = passwordEncoder;
        this.phoneNormalizer = phoneNormalizer;
        this.userSessionService = userSessionService;
        this.rateLimiter = rateLimiter;
        this.securityAuditService = securityAuditService;
        this.systemSettingService = systemSettingService;
    }

    @Transactional
    public void confirm(PasswordResetConfirmRequest request, String clientKey) {
        String phone;
        try {
            phone = phoneNormalizer.normalize(request.phone());
        } catch (InvalidRegistrationException exception) {
            securityAuditService.recordPasswordResetFailed(request.phone(), "PASSWORD_RESET_FAILED");
            throw new PasswordResetException("PASSWORD_RESET_FAILED", FAILED_MESSAGE);
        }

        rateLimiter.check("confirm:" + clientKey + ":" + phone);

        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new PasswordResetException("PASSWORD_CONFIRMATION_MISMATCH", "两次输入的密码不一致");
        }
        if (request.newPassword().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new PasswordResetException("PASSWORD_TOO_LONG", "密码内容过长");
        }
        int minLength = systemSettingService.passwordMinLengthSafe();
        if (request.newPassword().length() < minLength) {
            throw new PasswordResetException("PASSWORD_TOO_SHORT", "新密码至少 " + minLength + " 位");
        }

        AppUser user = userRepository.findByPhone(phone).orElse(null);
        if (user == null) {
            securityAuditService.recordPasswordResetFailed(phone, "PASSWORD_RESET_FAILED");
            throw new PasswordResetException("PASSWORD_RESET_FAILED", FAILED_MESSAGE);
        }

        try {
            smsVerificationService.verifyAndConsume(phone, SmsPurpose.RESET_PASSWORD, request.smsCode());
        } catch (SmsException exception) {
            securityAuditService.recordPasswordResetFailed(phone, "SMS_CODE_INVALID");
            throw new PasswordResetException("PASSWORD_RESET_FAILED", FAILED_MESSAGE);
        }

        user.changePassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        // 登录密码与保险箱加解密无关：仅吊销会话，保留账密记录
        userSessionService.deleteAllByPrincipal(user.getUsername());
        rateLimiter.clear("confirm:" + clientKey + ":" + phone);
        securityAuditService.recordPasswordResetSuccess(user.getId(), user.getUsername());
    }
}
