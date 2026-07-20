package com.godlei.onlinesafe.auth.application;

import com.godlei.onlinesafe.auth.domain.AppUser;
import com.godlei.onlinesafe.auth.infrastructure.AppUserRepository;
import com.godlei.onlinesafe.auth.web.RegistrationRequest;
import com.godlei.onlinesafe.auth.web.RegistrationResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class RegistrationService {

    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PhoneNormalizer phoneNormalizer;
    private final UsernameNormalizer usernameNormalizer;
    private final String invitationCode;

    public RegistrationService(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            PhoneNormalizer phoneNormalizer,
            UsernameNormalizer usernameNormalizer,
            @Value("${app.registration.invitation-code}") String invitationCode
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.phoneNormalizer = phoneNormalizer;
        this.usernameNormalizer = usernameNormalizer;
        this.invitationCode = invitationCode;
    }

    @Transactional
    public RegistrationResponse register(RegistrationRequest request) {
        validatePasswords(request.password(), request.confirmPassword());
        validateInvitationCode(request.invitationCode());

        String phone = phoneNormalizer.normalize(request.phone());
        UsernameNormalizer.UsernameValue username = usernameNormalizer.normalizeForRegistration(request.username());

        if (userRepository.existsByPhone(phone)
                || userRepository.existsByNormalizedUsername(username.normalized())) {
            throw new RegistrationConflictException();
        }

        AppUser user = AppUser.register(
                phone,
                username.display(),
                username.normalized(),
                passwordEncoder.encode(request.password())
        );

        try {
            AppUser saved = userRepository.saveAndFlush(user);
            return RegistrationResponse.from(saved);
        } catch (DataIntegrityViolationException exception) {
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
    }

    private void validateInvitationCode(String suppliedInvitationCode) {
        byte[] expected = invitationCode.trim().getBytes(StandardCharsets.UTF_8);
        byte[] supplied = suppliedInvitationCode.trim().getBytes(StandardCharsets.UTF_8);
        if (expected.length == 0 || !MessageDigest.isEqual(expected, supplied)) {
            throw new InvalidRegistrationException("INVITATION_CODE_INVALID", "邀请码无效");
        }
    }
}
