package com.godlei.onlinesafe.datarecovery.application;

import com.godlei.onlinesafe.audit.application.SecurityAuditService;
import com.godlei.onlinesafe.audit.domain.AuditActorType;
import com.godlei.onlinesafe.audit.domain.AuditEventType;
import com.godlei.onlinesafe.audit.domain.AuditResult;
import com.godlei.onlinesafe.datarecovery.web.TrashAssetResponse;
import com.godlei.onlinesafe.datarecovery.web.TrashAssetType;
import com.godlei.onlinesafe.datarecovery.web.TrashListType;
import com.godlei.onlinesafe.datarecovery.web.TrashPageResponse;
import com.godlei.onlinesafe.datarecovery.web.TrashPurgeResponse;
import com.godlei.onlinesafe.settings.application.SystemSettingService;
import com.godlei.onlinesafe.vault.application.InvalidVaultEnvelopeException;
import com.godlei.onlinesafe.vault.application.PrivateTemplateService;
import com.godlei.onlinesafe.vault.application.VaultItemService;
import com.godlei.onlinesafe.vault.application.VaultPayloadCipher;
import com.godlei.onlinesafe.vault.domain.PrivateTemplate;
import com.godlei.onlinesafe.vault.domain.VaultItem;
import com.godlei.onlinesafe.vault.infrastructure.PrivateTemplateRepository;
import com.godlei.onlinesafe.vault.infrastructure.VaultItemRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class VaultTrashService {

    private final VaultItemRepository itemRepository;
    private final PrivateTemplateRepository templateRepository;
    private final VaultPayloadCipher cipher;
    private final SystemSettingService systemSettingService;
    private final SecurityAuditService securityAuditService;
    private final Clock clock;

    public VaultTrashService(
            VaultItemRepository itemRepository,
            PrivateTemplateRepository templateRepository,
            VaultPayloadCipher cipher,
            SystemSettingService systemSettingService,
            SecurityAuditService securityAuditService,
            Clock clock
    ) {
        this.itemRepository = itemRepository;
        this.templateRepository = templateRepository;
        this.cipher = cipher;
        this.systemSettingService = systemSettingService;
        this.securityAuditService = securityAuditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TrashPageResponse list(String ownerId, String keyword, TrashListType type, int page, int size) {
        int retentionDays = systemSettingService.recycleBinRetentionDaysSafe();
        String normalized = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<TrashAssetResponse> all = new ArrayList<>();
        if (type == TrashListType.ALL || type == TrashListType.ITEM) {
            for (VaultItem item : itemRepository.findByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(
                    ownerId, PageRequest.of(0, 500)).getContent()) {
                TrashAssetResponse row = toItemRow(ownerId, item, retentionDays);
                if (matches(normalized, row)) {
                    all.add(row);
                }
            }
        }
        if (type == TrashListType.ALL || type == TrashListType.PRIVATE_TEMPLATE) {
            for (PrivateTemplate template : templateRepository.findByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(
                    ownerId, PageRequest.of(0, 500)).getContent()) {
                TrashAssetResponse row = toTemplateRow(ownerId, template, retentionDays);
                if (matches(normalized, row)) {
                    all.add(row);
                }
            }
        }
        all.sort(Comparator.comparing(TrashAssetResponse::deletedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(0, page);
        int from = Math.min(safePage * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        int totalPages = all.isEmpty() ? 0 : (int) Math.ceil(all.size() / (double) safeSize);
        return new TrashPageResponse(all.subList(from, to), safePage, safeSize, all.size(), totalPages);
    }

    @Transactional
    public void restore(String ownerId, String username, TrashAssetType type, String id) {
        if (type == TrashAssetType.ITEM) {
            VaultItem item = itemRepository.findByIdAndOwnerIdAndDeletedAtIsNotNull(id, ownerId)
                    .orElseThrow(() -> new DataRecoveryException("TRASH_ASSET_NOT_FOUND", "回收站中没有找到该数据"));
            item.restore();
            itemRepository.saveAndFlush(item);
        } else {
            PrivateTemplate template = templateRepository.findByIdAndOwnerIdAndDeletedAtIsNotNull(id, ownerId)
                    .orElseThrow(() -> new DataRecoveryException("TRASH_ASSET_NOT_FOUND", "回收站中没有找到该数据"));
            template.restore();
            templateRepository.saveAndFlush(template);
        }
        securityAuditService.recordInTx(
                AuditEventType.USER_TRASH_ASSET_RESTORED,
                AuditResult.SUCCESS,
                AuditActorType.USER,
                ownerId,
                username,
                type.name(),
                null,
                null,
                null,
                Map.of("assetType", type.name())
        );
    }

    @Transactional
    public void purgeOne(String ownerId, String username, TrashAssetType type, String id) {
        boolean deleted = false;
        if (type == TrashAssetType.ITEM) {
            deleted = itemRepository.findByIdAndOwnerIdAndDeletedAtIsNotNull(id, ownerId)
                    .map(item -> {
                        itemRepository.delete(item);
                        return true;
                    })
                    .orElse(false);
        } else {
            deleted = templateRepository.findByIdAndOwnerIdAndDeletedAtIsNotNull(id, ownerId)
                    .map(template -> {
                        templateRepository.delete(template);
                        return true;
                    })
                    .orElse(false);
        }
        if (deleted) {
            securityAuditService.recordInTx(
                    AuditEventType.USER_TRASH_ASSET_PURGED,
                    AuditResult.SUCCESS,
                    AuditActorType.USER,
                    ownerId,
                    username,
                    type.name(),
                    null,
                    null,
                    null,
                    Map.of("assetType", type.name())
            );
        }
    }

    @Transactional
    public TrashPurgeResponse purgeAll(String ownerId, String username) {
        List<VaultItem> items = itemRepository.findByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(
                ownerId, PageRequest.of(0, 5000)).getContent();
        List<PrivateTemplate> templates = templateRepository.findByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(
                ownerId, PageRequest.of(0, 5000)).getContent();
        if (!items.isEmpty()) {
            itemRepository.deleteAll(items);
        }
        if (!templates.isEmpty()) {
            templateRepository.deleteAll(templates);
        }
        securityAuditService.recordInTx(
                AuditEventType.USER_TRASH_EMPTIED,
                AuditResult.SUCCESS,
                AuditActorType.USER,
                ownerId,
                username,
                null,
                null,
                null,
                null,
                Map.of("itemCount", items.size(), "templateCount", templates.size())
        );
        return new TrashPurgeResponse(items.size(), templates.size());
    }

    @Transactional(readOnly = true)
    public long itemCount(String ownerId) {
        return itemRepository.countByOwnerIdAndDeletedAtIsNotNull(ownerId);
    }

    @Transactional(readOnly = true)
    public long templateCount(String ownerId) {
        return templateRepository.countByOwnerIdAndDeletedAtIsNotNull(ownerId);
    }

    @Transactional(readOnly = true)
    public Instant nearestPurgeAt(String ownerId, int retentionDays) {
        Instant itemDeleted = itemRepository.findFirstByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtAsc(ownerId)
                .map(VaultItem::getDeletedAt)
                .orElse(null);
        Instant templateDeleted = templateRepository.findFirstByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtAsc(ownerId)
                .map(PrivateTemplate::getDeletedAt)
                .orElse(null);
        Instant earliest = null;
        if (itemDeleted != null) {
            earliest = itemDeleted;
        }
        if (templateDeleted != null && (earliest == null || templateDeleted.isBefore(earliest))) {
            earliest = templateDeleted;
        }
        return earliest == null ? null : earliest.plus(retentionDays, ChronoUnit.DAYS);
    }

    private TrashAssetResponse toItemRow(String ownerId, VaultItem item, int retentionDays) {
        String name = "账密记录";
        String summary = "";
        try {
            JsonNode payload = cipher.decrypt(
                    ownerId,
                    VaultItemService.ENTITY_TYPE,
                    item.getId(),
                    item.getCiphertext(),
                    item.getNonce(),
                    item.getAlgoVersion(),
                    item.getPayloadVersion(),
                    item.getKeyId()
            );
            name = textOr(payload, "name", name);
            String platform = textOr(payload, "platform", "");
            String channel = textOr(payload, "channel", "");
            summary = joinSummary(platform, channel);
        } catch (InvalidVaultEnvelopeException ignored) {
            summary = "无法读取摘要";
        }
        return new TrashAssetResponse(
                item.getId(),
                TrashAssetType.ITEM.name(),
                name,
                summary,
                item.getDeletedAt(),
                remainingDays(item.getDeletedAt(), retentionDays)
        );
    }

    private TrashAssetResponse toTemplateRow(String ownerId, PrivateTemplate template, int retentionDays) {
        String name = "私人模板";
        String summary = "";
        try {
            JsonNode payload = cipher.decrypt(
                    ownerId,
                    PrivateTemplateService.ENTITY_TYPE,
                    template.getId(),
                    template.getCiphertext(),
                    template.getNonce(),
                    template.getAlgoVersion(),
                    template.getPayloadVersion(),
                    template.getKeyId()
            );
            name = textOr(payload, "name", name);
            summary = textOr(payload, "platform", "");
        } catch (InvalidVaultEnvelopeException ignored) {
            summary = "无法读取摘要";
        }
        return new TrashAssetResponse(
                template.getId(),
                TrashAssetType.PRIVATE_TEMPLATE.name(),
                name,
                summary,
                template.getDeletedAt(),
                remainingDays(template.getDeletedAt(), retentionDays)
        );
    }

    private int remainingDays(Instant deletedAt, int retentionDays) {
        if (deletedAt == null) {
            return 0;
        }
        Instant purgeAt = deletedAt.plus(retentionDays, ChronoUnit.DAYS);
        long days = ChronoUnit.DAYS.between(clock.instant(), purgeAt);
        return (int) Math.max(0, days);
    }

    private static boolean matches(String keyword, TrashAssetResponse row) {
        if (keyword.isBlank()) {
            return true;
        }
        return (row.name() != null && row.name().toLowerCase(Locale.ROOT).contains(keyword))
                || (row.summary() != null && row.summary().toLowerCase(Locale.ROOT).contains(keyword));
    }

    private static String textOr(JsonNode payload, String field, String fallback) {
        JsonNode node = payload.get(field);
        if (node == null || node.isNull() || !node.isValueNode()) {
            return fallback;
        }
        String text = node.asText("").trim();
        return text.isEmpty() ? fallback : text;
    }

    private static String joinSummary(String platform, String channel) {
        if (!platform.isBlank() && !channel.isBlank()) {
            return platform + " · " + channel;
        }
        return !platform.isBlank() ? platform : channel;
    }
}
