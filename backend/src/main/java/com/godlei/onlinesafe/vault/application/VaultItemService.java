package com.godlei.onlinesafe.vault.application;

import com.godlei.onlinesafe.vault.domain.VaultItem;
import com.godlei.onlinesafe.vault.infrastructure.VaultItemRepository;
import com.godlei.onlinesafe.vault.web.VaultRecordRequest;
import com.godlei.onlinesafe.vault.web.VaultRecordResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.List;

@Service
public class VaultItemService {

    public static final String ENTITY_TYPE = "ITEM";

    private final VaultItemRepository repository;
    private final VaultPayloadCipher cipher;

    public VaultItemService(VaultItemRepository repository, VaultPayloadCipher cipher) {
        this.repository = repository;
        this.cipher = cipher;
    }

    @Transactional(readOnly = true)
    public List<VaultRecordResponse> list(String ownerId) {
        return repository.findByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(ownerId).stream()
                .map(item -> toResponse(ownerId, item))
                .toList();
    }

    @Transactional(readOnly = true)
    public VaultRecordResponse get(String ownerId, String id) {
        return toResponse(ownerId, requireOwned(ownerId, id));
    }

    @Transactional
    public VaultRecordResponse create(String ownerId, VaultRecordRequest request) {
        VaultRecordSupport.requireUuid(request.id());
        VaultRecordSupport.requireItemPayload(request.payload());
        if (repository.existsById(request.id())) {
            throw new InvalidVaultEnvelopeException("ITEM_ID_CONFLICT", "记录 ID 已存在");
        }
        VaultPayloadCipher.SealedPayload sealed = cipher.encrypt(
                ownerId,
                ENTITY_TYPE,
                request.id().trim(),
                request.payload()
        );
        VaultItem saved = repository.saveAndFlush(VaultItem.create(
                request.id().trim(),
                ownerId,
                sealed.ciphertext(),
                sealed.nonce(),
                sealed.algoVersion(),
                sealed.payloadVersion(),
                sealed.keyId()
        ));
        return toResponse(ownerId, saved);
    }

    @Transactional
    public VaultRecordResponse update(String ownerId, String id, VaultRecordRequest request) {
        VaultItem item = requireOwned(ownerId, id);
        if (request.revision() != item.getVersion()) {
            throw new VaultRevisionConflictException();
        }
        VaultRecordSupport.requireItemPayload(request.payload());
        VaultPayloadCipher.SealedPayload sealed = cipher.encrypt(
                ownerId,
                ENTITY_TYPE,
                id,
                request.payload()
        );
        item.replaceCiphertext(
                sealed.ciphertext(),
                sealed.nonce(),
                sealed.algoVersion(),
                sealed.payloadVersion(),
                sealed.keyId()
        );
        return toResponse(ownerId, repository.saveAndFlush(item));
    }

    @Transactional
    public void delete(String ownerId, String id) {
        VaultItem item = requireOwned(ownerId, id);
        item.softDelete();
        repository.saveAndFlush(item);
    }

    private VaultItem requireOwned(String ownerId, String id) {
        return repository.findByIdAndOwnerIdAndDeletedAtIsNull(id, ownerId)
                .orElseThrow(VaultItemNotFoundException::new);
    }

    private VaultRecordResponse toResponse(String ownerId, VaultItem item) {
        JsonNode payload = cipher.decrypt(
                ownerId,
                ENTITY_TYPE,
                item.getId(),
                item.getCiphertext(),
                item.getNonce(),
                item.getAlgoVersion(),
                item.getPayloadVersion(),
                item.getKeyId()
        );
        return new VaultRecordResponse(
                item.getId(),
                payload,
                item.getVersion(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
