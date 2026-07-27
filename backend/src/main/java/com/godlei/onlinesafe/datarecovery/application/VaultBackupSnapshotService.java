package com.godlei.onlinesafe.datarecovery.application;

import com.godlei.onlinesafe.audit.application.SecurityAuditService;
import com.godlei.onlinesafe.audit.domain.AuditActorType;
import com.godlei.onlinesafe.audit.domain.AuditEventType;
import com.godlei.onlinesafe.audit.domain.AuditResult;
import com.godlei.onlinesafe.datarecovery.domain.DataRecoveryOperation;
import com.godlei.onlinesafe.datarecovery.domain.RecoveryOperationStatus;
import com.godlei.onlinesafe.datarecovery.domain.RecoveryOperationType;
import com.godlei.onlinesafe.datarecovery.infrastructure.DataRecoveryOperationRepository;
import com.godlei.onlinesafe.datarecovery.web.BackupSnapshotRequest;
import com.godlei.onlinesafe.datarecovery.web.BackupSnapshotResponse;
import com.godlei.onlinesafe.vault.application.PrivateTemplateService;
import com.godlei.onlinesafe.vault.application.VaultItemService;
import com.godlei.onlinesafe.vault.application.VaultPayloadCipher;
import com.godlei.onlinesafe.vault.domain.PrivateTemplate;
import com.godlei.onlinesafe.vault.domain.VaultItem;
import com.godlei.onlinesafe.vault.infrastructure.PrivateTemplateRepository;
import com.godlei.onlinesafe.vault.infrastructure.VaultItemRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class VaultBackupSnapshotService {

    public static final int FORMAT_VERSION = 1;

    private final VaultItemRepository itemRepository;
    private final PrivateTemplateRepository templateRepository;
    private final VaultPayloadCipher cipher;
    private final DataRecoveryOperationRepository operationRepository;
    private final SecurityAuditService securityAuditService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String applicationVersion;
    private final int maxItems;
    private final int maxTemplates;

    public VaultBackupSnapshotService(
            VaultItemRepository itemRepository,
            PrivateTemplateRepository templateRepository,
            VaultPayloadCipher cipher,
            DataRecoveryOperationRepository operationRepository,
            SecurityAuditService securityAuditService,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${app.version:dev}") String applicationVersion,
            @Value("${app.data-recovery.max-items:5000}") int maxItems,
            @Value("${app.data-recovery.max-templates:500}") int maxTemplates
    ) {
        this.itemRepository = itemRepository;
        this.templateRepository = templateRepository;
        this.cipher = cipher;
        this.operationRepository = operationRepository;
        this.securityAuditService = securityAuditService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.applicationVersion = applicationVersion;
        this.maxItems = maxItems;
        this.maxTemplates = maxTemplates;
    }

    @Transactional(readOnly = true)
    public Instant lastSnapshotAt(String ownerId) {
        return operationRepository.findFirstByOwnerIdAndOperationTypeOrderByCreatedAtDesc(
                        ownerId, RecoveryOperationType.BACKUP_SNAPSHOT)
                .map(DataRecoveryOperation::getCreatedAt)
                .orElse(null);
    }

    @Transactional
    public BackupSnapshotResponse createSnapshot(String ownerId, String username, BackupSnapshotRequest request) {
        boolean includeTrash = request == null || request.includeTrash() == null || request.includeTrash();
        List<VaultItem> items = includeTrash
                ? itemRepository.findByOwnerIdOrderByUpdatedAtDesc(ownerId)
                : itemRepository.findByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(ownerId);
        List<PrivateTemplate> templates = includeTrash
                ? templateRepository.findByOwnerIdOrderByUpdatedAtDesc(ownerId)
                : templateRepository.findByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(ownerId);
        if (items.size() > maxItems || templates.size() > maxTemplates) {
            throw new DataRecoveryException("BACKUP_LIMIT_EXCEEDED", "备份内容超过允许范围");
        }

        String backupId = UUID.randomUUID().toString();
        Instant now = clock.instant();
        ObjectNode root = objectMapper.createObjectNode();
        root.put("backupId", backupId);
        root.put("schemaVersion", FORMAT_VERSION);
        root.put("createdAt", now.toString());
        ObjectNode source = root.putObject("source");
        source.put("application", "online-safe");
        source.put("applicationVersion", applicationVersion);
        source.put("accountId", ownerId);
        ObjectNode scope = root.putObject("scope");
        scope.put("includesTrash", includeTrash);

        ArrayNode itemNodes = root.putArray("items");
        for (VaultItem item : items) {
            itemNodes.add(toItemNode(ownerId, item));
        }
        ArrayNode templateNodes = root.putArray("privateTemplates");
        for (PrivateTemplate template : templates) {
            templateNodes.add(toTemplateNode(ownerId, template));
        }

        DataRecoveryOperation op = DataRecoveryOperation.createUser(
                ownerId,
                RecoveryOperationType.BACKUP_SNAPSHOT,
                FORMAT_VERSION,
                backupId,
                items.size() + templates.size(),
                null
        );
        op.markRunning(now);
        op.addBatchCounts(items.size() + templates.size(), items.size(), 0, 0, 0);
        op.complete(RecoveryOperationStatus.SUCCEEDED, now, null);
        operationRepository.save(op);

        securityAuditService.recordInTx(
                AuditEventType.USER_BACKUP_SNAPSHOT_CREATED,
                AuditResult.SUCCESS,
                AuditActorType.USER,
                ownerId,
                username,
                null,
                null,
                null,
                null,
                Map.of(
                        "itemCount", items.size(),
                        "templateCount", templates.size(),
                        "includesTrash", includeTrash,
                        "formatVersion", FORMAT_VERSION
                )
        );
        return new BackupSnapshotResponse(root);
    }

    private ObjectNode toItemNode(String ownerId, VaultItem item) {
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
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", item.getId());
        node.set("payload", payload);
        node.put("revision", item.getVersion());
        node.put("createdAt", item.getCreatedAt().toString());
        node.put("updatedAt", item.getUpdatedAt().toString());
        if (item.getDeletedAt() == null) {
            node.putNull("deletedAt");
        } else {
            node.put("deletedAt", item.getDeletedAt().toString());
        }
        return node;
    }

    private ObjectNode toTemplateNode(String ownerId, PrivateTemplate template) {
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
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", template.getId());
        node.set("payload", payload);
        node.put("revision", template.getVersion());
        node.put("createdAt", template.getCreatedAt().toString());
        node.put("updatedAt", template.getUpdatedAt().toString());
        if (template.getDeletedAt() == null) {
            node.putNull("deletedAt");
        } else {
            node.put("deletedAt", template.getDeletedAt().toString());
        }
        return node;
    }
}
