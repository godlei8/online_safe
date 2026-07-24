package com.godlei.onlinesafe.auth.application;

import com.godlei.onlinesafe.admin.application.InvitationService;
import com.godlei.onlinesafe.audit.application.SecurityAuditService;
import com.godlei.onlinesafe.auth.domain.AppUser;
import com.godlei.onlinesafe.auth.domain.SmsPurpose;
import com.godlei.onlinesafe.auth.infrastructure.AppUserRepository;
import com.godlei.onlinesafe.auth.web.RegistrationRequest;
import com.godlei.onlinesafe.auth.web.RegistrationResponse;
import com.godlei.onlinesafe.settings.application.SystemSettingService;
import com.godlei.onlinesafe.settings.domain.RegistrationMode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Service
public class RegistrationService {

    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PhoneNormalizer phoneNormalizer;
    private final UsernameNormalizer usernameNormalizer;
    private final SmsVerificationService smsVerificationService;
    private final InvitationService invitationService;
    private final SystemSettingService systemSettingService;
    private final SecurityAuditService securityAuditService;

    public RegistrationService(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            PhoneNormalizer phoneNormalizer,
            UsernameNormalizer usernameNormalizer,
            SmsVerificationService smsVerificationService,
            InvitationService invitationService,
            SystemSettingService systemSettingService,
            SecurityAuditService securityAuditService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.phoneNormalizer = phoneNormalizer;
        this.usernameNormalizer = usernameNormalizer;
        this.smsVerificationService = smsVerificationService;
        this.invitationService = invitationService;
        this.systemSettingService = systemSettingService;
        this.securityAuditService = securityAuditService;
    }

    @Transactional
    public RegistrationResponse register(RegistrationRequest request) {
        RegistrationMode mode = systemSettingService.registrationModeSafe();
        if (mode == RegistrationMode.CLOSED) {
            securityAuditService.recordRegistrationFailed(request.phone(), "REGISTRATION_CLOSED");
            throw new InvalidRegistrationException("REGISTRATION_CLOSED", "当前已关闭新用户注册");
        }

        try {
            validatePasswords(request.password(), request.confirmPassword());
        } catch (InvalidRegistrationException exception) {
            securityAuditService.recordRegistrationFailed(request.username(), exception.getCode());
            throw exception;
        }

        String phone;
        UsernameNormalizer.UsernameValue username;
        try {
            phone = phoneNormalizer.normalize(request.phone());
            username = usernameNormalizer.normalizeForRegistration(request.username());
        } catch (InvalidRegistrationException exception) {
            securityAuditService.recordRegistrationFailed(request.phone(), exception.getCode());
            throw exception;
        }

        if (userRepository.existsByPhone(phone)
                || userRepository.existsByNormalizedUsername(username.normalized())) {
            securityAuditService.recordRegistrationFailed(phone, "ACCOUNT_IDENTIFIER_ALREADY_EXISTS");
            throw new RegistrationConflictException();
        }

        try {
            smsVerificationService.verifyAndConsume(phone, SmsPurpose.REGISTER, request.smsCode());
        } catch (SmsException exception) {
            securityAuditService.recordRegistrationFailed(phone, exception.getCode());
            throw exception;
        }

        boolean inviteRequired = mode == RegistrationMode.INVITE_AND_SMS;
        if (inviteRequired && (request.inviteCode() == null || request.inviteCode().isBlank())) {
            securityAuditService.recordRegistrationFailed(phone, "INVITATION_CODE_REQUIRED");
            throw new InvalidRegistrationException("INVITATION_CODE_REQUIRED", "请填写邀请码");
        }

        AppUser user = AppUser.register(
                phone,
                username.display(),
                username.normalized(),
                passwordEncoder.encode(request.password())
        );
        user.markPhoneVerified();

        try {
            AppUser saved = userRepository.saveAndFlush(user);
            if (inviteRequired) {
                invitationService.consumeForRegistration(request.inviteCode().trim(), saved.getId());
            }
            securityAuditService.recordRegistrationSuccess(saved.getId(), saved.getUsername(), inviteRequired);
            return RegistrationResponse.from(saved);
        } catch (DataIntegrityViolationException exception) {
            securityAuditService.recordRegistrationFailed(phone, "ACCOUNT_IDENTIFIER_ALREADY_EXISTS");
            throw new RegistrationConflictException();
        }
    }

    private void validatePasswords(String password, String confirmPassword) {
        if (!password.equals(confirmPassword)) {
            throw new InvalidRegistrationException("PASSWORD_CONFIRMATION_MISMATCH", "两次输入的密码不一致");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new InvalidRegistrationException("PASSWORD_TOO_LONG", "密码内容过长");
        }
        int minLength = systemSettingService.passwordMinLengthSafe();
        if (password.length() < minLength) {
            throw new InvalidRegistrationException("PASSWORD_TOO_SHORT", "登录密码至少 " + minLength + " 位");
        }
    }
}
