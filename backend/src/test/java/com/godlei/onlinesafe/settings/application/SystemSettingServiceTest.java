package com.godlei.onlinesafe.settings.application;

import com.godlei.onlinesafe.audit.application.SecurityAuditService;
import com.godlei.onlinesafe.settings.domain.RegistrationMode;
import com.godlei.onlinesafe.settings.infrastructure.SystemSettingRepository;
import com.godlei.onlinesafe.settings.web.RegistrationPolicyResponse;
import com.godlei.onlinesafe.settings.web.SystemSettingChangeRequest;
import com.godlei.onlinesafe.settings.web.SystemSettingUpdateRequest;
import com.godlei.onlinesafe.settings.web.SystemSettingValidateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SystemSettingServiceTest {

    @Mock
    private SystemSettingRepository repository;
    @Mock
    private SystemSettingCapabilityService capabilityService;
    @Mock
    private SecurityAuditService securityAuditService;

    private SystemSettingService service;

    @BeforeEach
    void setUp() {
        service = new SystemSettingService(
                new SystemSettingRegistry(),
                repository,
                capabilityService,
                securityAuditService,
                Clock.fixed(Instant.parse("2026-07-24T00:00:00Z"), ZoneOffset.UTC)
        );
        when(repository.findAll()).thenReturn(List.of());
    }

    @Test
    void registrationPolicyDefaultsToSmsVerified() {
        RegistrationPolicyResponse policy = service.registrationPolicy();
        assertThat(policy.registrationEnabled()).isTrue();
        assertThat(policy.mode()).isEqualTo(RegistrationMode.SMS_VERIFIED.name());
        assertThat(policy.inviteRequired()).isFalse();
        assertThat(policy.passwordMinLength()).isEqualTo(8);
    }

    @Test
    void validateClosedRegistrationReturnsHighRisk() {
        SystemSettingValidateResponse response = service.validate(new SystemSettingUpdateRequest(List.of(
                new SystemSettingChangeRequest(SystemSettingRegistry.REGISTRATION_MODE, "CLOSED", 0L)
        )));
        assertThat(response.valid()).isTrue();
        assertThat(response.highestRisk()).isEqualTo("HIGH");
        assertThat(response.confirmationTitle()).contains("关闭");
        assertThat(response.effects()).isNotEmpty();
    }
}
