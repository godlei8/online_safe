package com.godlei.onlinesafe.auth.application;

import com.godlei.onlinesafe.audit.application.SecurityAuditService;
import com.godlei.onlinesafe.auth.domain.SmsPurpose;
import com.godlei.onlinesafe.auth.domain.SmsVerification;
import com.godlei.onlinesafe.auth.infrastructure.AppUserRepository;
import com.godlei.onlinesafe.auth.infrastructure.SmsVerificationRepository;
import com.godlei.onlinesafe.sms.SmsProperties;
import com.godlei.onlinesafe.sms.SmsSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class SmsVerificationService {

    private final SmsVerificationRepository repository;
    private final AppUserRepository userRepository;
    private final SmsSender smsSender;
    private final SmsProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final PhoneNormalizer phoneNormalizer;
    private final SecurityAuditService securityAuditService;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public SmsVerificationService(
            SmsVerificationRepository repository,
            AppUserRepository userRepository,
            SmsSender smsSender,
            SmsProperties properties,
            PasswordEncoder passwordEncoder,
            PhoneNormalizer phoneNormalizer,
            SecurityAuditService securityAuditService,
            Clock clock
    ) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.smsSender = smsSender;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.phoneNormalizer = phoneNormalizer;
        this.securityAuditService = securityAuditService;
        this.clock = clock;
    }

    @Transactional
    public void send(String rawPhone, SmsPurpose purpose, String clientIp) {
        String phone = phoneNormalizer.normalize(rawPhone);
        Instant now = clock.instant();

        switch (purpose) {
            case REGISTER -> {
                if (userRepository.existsByPhone(phone)) {
                    throw new SmsException("PHONE_ALREADY_REGISTERED", "该手机号已注册");
                }
            }
            case RESET_PASSWORD -> {
                if (!userRepository.existsByPhone(phone)) {
                    // 与重置确认一致：不暴露是否注册时可返回同一文案；发送阶段需已注册手机
                    throw new SmsException("PHONE_NOT_REGISTERED", "该手机号未注册，无法发送验证码");
                }
            }
        }

        repository.findFirstByPhoneAndPurposeOrderByCreatedAtDesc(phone, purpose).ifPresent(latest -> {
            Instant earliestNext = latest.getCreatedAt().plusSeconds(properties.sendIntervalSeconds());
            if (earliestNext.isAfter(now)) {
                securityAuditService.recordSmsBlocked(phone, purpose.name(), "SMS_SEND_TOO_FREQUENT");
                throw new SmsException("SMS_SEND_TOO_FREQUENT", "发送过于频繁，请稍后再试");
            }
        });

        Instant dayStart = now.truncatedTo(ChronoUnit.DAYS);
        long dailyCount = repository.countByPhoneAndPurposeAndCreatedAtAfter(phone, purpose, dayStart);
        if (dailyCount >= properties.sendDailyLimit()) {
            securityAuditService.recordSmsBlocked(phone, purpose.name(), "SMS_SEND_DAILY_LIMIT");
            throw new SmsException("SMS_SEND_DAILY_LIMIT", "今日发送次数已达上限");
        }

        String code = generateCode(properties.codeLength());
        Instant expiresAt = now.plusSeconds(properties.codeTtlSeconds());
        SmsVerification verification = SmsVerification.create(
                phone,
                purpose,
                passwordEncoder.encode(code),
                expiresAt,
                (int) dailyCount + 1,
                clientIp
        );
        repository.save(verification);
        smsSender.send(phone, purpose, code);
    }

    @Transactional
    public void verifyAndConsume(String rawPhone, SmsPurpose purpose, String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new SmsException("SMS_CODE_INVALID", "验证码不正确或已失效");
        }
        String phone = phoneNormalizer.normalize(rawPhone);
        Instant now = clock.instant();
        List<SmsVerification> candidates = repository
                .findByPhoneAndPurposeAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(phone, purpose, now);
        if (candidates.isEmpty()) {
            throw new SmsException("SMS_CODE_INVALID", "验证码不正确或已失效");
        }

        SmsVerification latest = candidates.getFirst();
        if (!passwordEncoder.matches(rawCode.trim(), latest.getCodeHash())) {
            throw new SmsException("SMS_CODE_INVALID", "验证码不正确或已失效");
        }
        latest.consume(now);
        repository.save(latest);
    }

    private String generateCode(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(secureRandom.nextInt(10));
        }
        return builder.toString();
    }
}
