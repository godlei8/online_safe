package com.godlei.onlinesafe.datarecovery.application;

import com.godlei.onlinesafe.audit.application.SecurityAuditService;
import com.godlei.onlinesafe.audit.domain.AuditActorType;
import com.godlei.onlinesafe.audit.domain.AuditEventType;
import com.godlei.onlinesafe.audit.domain.AuditResult;
import com.godlei.onlinesafe.vault.application.InvalidVaultEnvelopeException;
import com.godlei.onlinesafe.vault.application.PrivateTemplateService;
import com.godlei.onlinesafe.vault.application.VaultItemService;
import com.godlei.onlinesafe.vault.application.VaultKeyRing;
import com.godlei.onlinesafe.vault.application.VaultPayloadCipher;
import com.godlei.onlinesafe.vault.domain.PrivateTemplate;
import com.godlei.onlinesafe.vault.domain.VaultItem;
import com.godlei.onlinesafe.vault.infrastructure.PrivateTemplateRepository;
import com.godlei.onlinesafe.vault.infrastructure.VaultItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VaultIntegrityScanService {

    private static final Duration MANUAL_COOLDOWN = Duration.ofHours(24);

    private final VaultItemRepository itemRepository;
    private final PrivateTemplateRepository templateRepository;
    private final VaultPayloadCipher cipher;
    private final VaultKeyRing keyRing;
    private final SecurityAuditService securityAuditService;
    private final Clock clock;
    private final ConcurrentHashMap<String, UserScanStatus> statusByOwner = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lastManualScan = new ConcurrentHashMap<>();

    public VaultIntegrityScanService(
            VaultItemRepository itemRepository,
            PrivateTemplateRepository templateRepository,
            VaultPayloadCipher cipher,
            VaultKeyRing keyRing,
            SecurityAuditService securityAuditService,
            Clock clock
    ) {
        this.itemRepository = itemRepository;
        this.templateRepository = templateRepository;
        this.cipher = cipher;
        this.keyRing = keyRing;
        this.securityAuditService = securityAuditService;
        this.clock = clock;
    }

    public UserScanStatus userStatus(String ownerId) {
        return statusByOwner.getOrDefault(ownerId, new UserScanStatus("NOT_CHECKED", null));
    }

    @Transactional(readOnly = true)
    public UserScanStatus scanForUser(String ownerId, String username) {
        Instant now = clock.instant();
        Instant last = lastManualScan.get(ownerId);
        if (last != null && last.plus(MANUAL_COOLDOWN).isAfter(now)) {
            throw new DataRecoveryException("REAUTH_RATE_LIMITED", "完整性检查过于频繁，请稍后再试");
        }
        int checked = 0;
        int failed = 0;
        try {
            for (VaultItem item : itemRepository.findByOwnerIdOrderByUpdatedAtDesc(ownerId)) {
                checked++;
                if (!checkItem(ownerId, item)) {
                    failed++;
                }
            }
            for (PrivateTemplate template : templateRepository.findByOwnerIdOrderByUpdatedAtDesc(ownerId)) {
                checked++;
                if (!checkTemplate(ownerId, template)) {
                    failed++;
                }
            }
            String status = failed == 0 ? "HEALTHY" : "NEEDS_ATTENTION";
            UserScanStatus result = new UserScanStatus(status, now);
            statusByOwner.put(ownerId, result);
            lastManualScan.put(ownerId, now);
            securityAuditService.recordInTx(
                    AuditEventType.VAULT_INTEGRITY_SCAN_SUCCEEDED,
                    AuditResult.SUCCESS,
                    AuditActorType.USER,
                    ownerId,
                    username,
                    null,
                    null,
                    null,
                    null,
                    Map.of("checkedCount", checked, "failedCount", failed)
            );
            return result;
        } catch (DataRecoveryException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            securityAuditService.recordInTx(
                    AuditEventType.VAULT_INTEGRITY_SCAN_FAILED,
                    AuditResult.FAILED,
                    AuditActorType.USER,
                    ownerId,
                    username,
                    null,
                    null,
                    null,
                    "SCAN_FAILED",
                    Map.of("errorCode", "SCAN_FAILED")
            );
            throw new DataRecoveryException("RESTORE_FAILED", "完整性检查未完成，请稍后重试");
        }
    }

    private boolean checkItem(String ownerId, VaultItem item) {
        if (!keyRing.hasKey(item.getKeyId())) {
            return false;
        }
        try {
            cipher.decrypt(
                    ownerId,
                    VaultItemService.ENTITY_TYPE,
                    item.getId(),
                    item.getCiphertext(),
                    item.getNonce(),
                    item.getAlgoVersion(),
                    item.getPayloadVersion(),
                    item.getKeyId()
            );
            return true;
        } catch (InvalidVaultEnvelopeException exception) {
            return false;
        }
    }

    private boolean checkTemplate(String ownerId, PrivateTemplate template) {
        if (!keyRing.hasKey(template.getKeyId())) {
            return false;
        }
        try {
            cipher.decrypt(
                    ownerId,
                    PrivateTemplateService.ENTITY_TYPE,
                    template.getId(),
                    template.getCiphertext(),
                    template.getNonce(),
                    template.getAlgoVersion(),
                    template.getPayloadVersion(),
                    template.getKeyId()
            );
            return true;
        } catch (InvalidVaultEnvelopeException exception) {
            return false;
        }
    }

    public record UserScanStatus(String status, Instant lastCheckedAt) {
    }
}
