package com.godlei.onlinesafe.admin.application;

import com.godlei.onlinesafe.admin.domain.AdminUser;
import com.godlei.onlinesafe.admin.domain.InviteStatus;
import com.godlei.onlinesafe.admin.domain.RegistrationInvite;
import com.godlei.onlinesafe.admin.domain.RegistrationInviteRedemption;
import com.godlei.onlinesafe.admin.infrastructure.AdminUserRepository;
import com.godlei.onlinesafe.admin.infrastructure.RegistrationInviteRedemptionRepository;
import com.godlei.onlinesafe.admin.infrastructure.RegistrationInviteRepository;
import com.godlei.onlinesafe.admin.web.CreateInvitationRequest;
import com.godlei.onlinesafe.admin.web.CreateInvitationResponse;
import com.godlei.onlinesafe.admin.web.InvitationResponse;
import com.godlei.onlinesafe.admin.web.InvitationStatsResponse;
import com.godlei.onlinesafe.admin.web.PlainInvitationCodeResponse;
import com.godlei.onlinesafe.auth.application.InvalidRegistrationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class InvitationService {

    private final RegistrationInviteRepository inviteRepository;
    private final RegistrationInviteRedemptionRepository redemptionRepository;
    private final AdminUserRepository adminUserRepository;
    private final InvitationCodeGenerator codeGenerator;
    private final InvitationCodeHasher codeHasher;
    private final InvitationCodeCipher codeCipher;
    private final Clock clock;

    public InvitationService(
            RegistrationInviteRepository inviteRepository,
            RegistrationInviteRedemptionRepository redemptionRepository,
            AdminUserRepository adminUserRepository,
            InvitationCodeGenerator codeGenerator,
            InvitationCodeHasher codeHasher,
            InvitationCodeCipher codeCipher,
            Clock clock
    ) {
        this.inviteRepository = inviteRepository;
        this.redemptionRepository = redemptionRepository;
        this.adminUserRepository = adminUserRepository;
        this.codeGenerator = codeGenerator;
        this.codeHasher = codeHasher;
        this.codeCipher = codeCipher;
        this.clock = clock;
    }

    @Transactional
    public CreateInvitationResponse create(String adminId, CreateInvitationRequest request) {
        Instant now = clock.instant();
        if (request.expiresAt() != null && !request.expiresAt().isAfter(now)) {
            throw new InvalidInvitationOperationException("INVITATION_EXPIRES_AT_INVALID", "过期时间必须晚于当前时间");
        }

        String plainCode = codeGenerator.generate();
        RegistrationInvite invite = RegistrationInvite.create(
                codeHasher.hash(plainCode),
                codeHasher.hint(plainCode),
                codeCipher.encrypt(plainCode),
                request.maxUses(),
                request.expiresAt(),
                adminId,
                blankToNull(request.note())
        );
        inviteRepository.save(invite);
        return CreateInvitationResponse.from(toResponse(invite, creatorName(adminId)), plainCode);
    }

    @Transactional(readOnly = true)
    public Page<InvitationResponse> list(InviteStatus status, Boolean singleUse, String query, Pageable pageable) {
        Instant now = clock.instant();
        Page<RegistrationInvite> page = inviteRepository.search(status, singleUse, blankToNull(query), pageable);
        page.forEach(invite -> invite.refreshDerivedStatus(now));
        Map<String, String> creators = loadCreatorNames(page.getContent());
        return page.map(invite -> toResponse(invite, creators.getOrDefault(invite.getCreatedByAdminId(), "未知")));
    }

    @Transactional(readOnly = true)
    public InvitationStatsResponse stats() {
        Instant now = clock.instant();
        Instant horizon = now.plus(Duration.ofHours(48));
        long total = inviteRepository.count();
        long active = inviteRepository.countByStatus(InviteStatus.ACTIVE);
        long expiringSoon = inviteRepository.countExpiringSoon(InviteStatus.ACTIVE, now, horizon);
        long disabledOrExhausted = inviteRepository.countByStatus(InviteStatus.DISABLED)
                + inviteRepository.countByStatus(InviteStatus.EXHAUSTED)
                + inviteRepository.countByStatus(InviteStatus.EXPIRED);
        return new InvitationStatsResponse(total, active, expiringSoon, disabledOrExhausted);
    }

    @Transactional(readOnly = true)
    public PlainInvitationCodeResponse revealPlainCode(String inviteId) {
        RegistrationInvite invite = inviteRepository.findById(inviteId)
                .orElseThrow(InvitationNotFoundException::new);
        if (invite.getCodeEncrypted() == null || invite.getCodeEncrypted().isBlank()) {
            throw new InvalidInvitationOperationException("INVITATION_CODE_UNAVAILABLE", "邀请码明文不可用，请新建邀请码");
        }
        String plainCode = codeCipher.decrypt(invite.getCodeEncrypted());
        return new PlainInvitationCodeResponse(invite.getId(), invite.getCodeHint(), plainCode);
    }

    @Transactional
    public void delete(String inviteId) {
        RegistrationInvite invite = inviteRepository.findById(inviteId)
                .orElseThrow(InvitationNotFoundException::new);
        redemptionRepository.deleteByInviteId(invite.getId());
        inviteRepository.delete(invite);
    }

    @Transactional
    public void consumeForRegistration(String rawCode, String userId) {
        Instant now = clock.instant();
        String hash = codeHasher.hash(rawCode);
        RegistrationInvite invite = inviteRepository.findByCodeHash(hash)
                .orElseThrow(() -> new InvalidRegistrationException("INVITATION_CODE_INVALID", "邀请码无效"));
        if (!invite.isUsable(now)) {
            throw new InvalidRegistrationException("INVITATION_CODE_INVALID", "邀请码无效");
        }
        invite.redeem(now);
        redemptionRepository.save(RegistrationInviteRedemption.of(invite.getId(), userId));
    }

    private InvitationResponse toResponse(RegistrationInvite invite, String creatorUsername) {
        Instant now = clock.instant();
        invite.refreshDerivedStatus(now);
        String displayStatus = invite.getStatus().name();
        if (invite.isExpiringSoon(now, now.plus(Duration.ofHours(48)))) {
            displayStatus = "ACTIVE_ATTENTION";
        }
        return new InvitationResponse(
                invite.getId(),
                invite.getCodeHint(),
                invite.isSingleUse() ? "SINGLE" : "MULTI",
                invite.getUsedCount(),
                invite.getMaxUses(),
                invite.getExpiresAt(),
                displayStatus,
                creatorUsername,
                invite.getLastUsedAt(),
                invite.getNote(),
                invite.getCreatedAt()
        );
    }

    private String creatorName(String adminId) {
        return adminUserRepository.findById(adminId).map(AdminUser::getUsername).orElse("未知");
    }

    private Map<String, String> loadCreatorNames(List<RegistrationInvite> invites) {
        Set<String> ids = invites.stream().map(RegistrationInvite::getCreatedByAdminId).collect(Collectors.toSet());
        Map<String, String> names = new HashMap<>();
        adminUserRepository.findAllById(ids).forEach(admin -> names.put(admin.getId(), admin.getUsername()));
        return names;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
