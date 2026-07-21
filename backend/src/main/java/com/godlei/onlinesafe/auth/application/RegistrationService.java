package com.godlei.onlinesafe.auth.application;

import com.godlei.onlinesafe.admin.application.InvitationService;
import com.godlei.onlinesafe.auth.domain.AppUser;
import com.godlei.onlinesafe.auth.infrastructure.AppUserRepository;
import com.godlei.onlinesafe.auth.web.RegistrationRequest;
import com.godlei.onlinesafe.auth.web.RegistrationResponse;
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
    private final InvitationService invitationService;
    private final SecurityQuestionService securityQuestionService;

    public RegistrationService(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            PhoneNormalizer phoneNormalizer,
            UsernameNormalizer usernameNormalizer,
            InvitationService invitationService,
            SecurityQuestionService securityQuestionService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.phoneNormalizer = phoneNormalizer;
        this.usernameNormalizer = usernameNormalizer;
        this.invitationService = invitationService;
        this.securityQuestionService = securityQuestionService;
    }

    @Transactional
    public RegistrationResponse register(RegistrationRequest request) {
        validatePasswords(request.password(), request.confirmPassword());

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
            securityQuestionService.saveForRegistration(saved.getId(), request.securityQuestions());
            invitationService.consumeForRegistration(request.invitationCode(), saved.getId());
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
}
