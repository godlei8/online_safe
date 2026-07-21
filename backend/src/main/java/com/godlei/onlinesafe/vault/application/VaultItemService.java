package com.godlei.onlinesafe.vault.application;

import com.godlei.onlinesafe.vault.domain.VaultItem;
import com.godlei.onlinesafe.vault.infrastructure.VaultItemRepository;
import com.godlei.onlinesafe.vault.web.VaultCipherEnvelopeRequest;
import com.godlei.onlinesafe.vault.web.VaultCipherEnvelopeResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class VaultItemService {

    private final VaultItemRepository repository;

    public VaultItemService(VaultItemRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<VaultCipherEnvelopeResponse> list(String ownerId) {
        return repository.findByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(ownerId).stream()
                .map(VaultItemService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public VaultCipherEnvelopeResponse get(String ownerId, String id) {
        return toResponse(requireOwned(ownerId, id));
    }

    @Transactional
    public VaultCipherEnvelopeResponse create(String ownerId, VaultCipherEnvelopeRequest request) {
        VaultEnvelopeSupport.requireUuid(request.id());
        if (repository.existsById(request.id())) {
            throw new InvalidVaultEnvelopeException("ITEM_ID_CONFLICT", "记录 ID 已存在");
        }
        DecodedEnvelope envelope = decode(request);
        VaultItem saved = repository.saveAndFlush(VaultItem.create(
                request.id().trim(),
                ownerId,
                envelope.ciphertext(),
                envelope.nonce(),
                envelope.algoVersion(),
                envelope.payloadVersion()
        ));
        return toResponse(saved);
    }

    @Transactional
    public VaultCipherEnvelopeResponse update(String ownerId, String id, VaultCipherEnvelopeRequest request) {
        VaultItem item = requireOwned(ownerId, id);
        if (request.revision() != item.getVersion()) {
            throw new VaultRevisionConflictException();
        }
        DecodedEnvelope envelope = decode(request);
        item.replaceCiphertext(
                envelope.ciphertext(),
                envelope.nonce(),
                envelope.algoVersion(),
                envelope.payloadVersion()
        );
        return toResponse(repository.saveAndFlush(item));
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

    private static DecodedEnvelope decode(VaultCipherEnvelopeRequest request) {
        byte[] ciphertext = VaultEnvelopeSupport.decodeBase64(request.ciphertextBase64(), "ciphertextBase64");
        byte[] nonce = VaultEnvelopeSupport.decodeBase64(request.nonceBase64(), "nonceBase64");
        VaultEnvelopeSupport.requireNonEmpty(ciphertext, "ciphertextBase64");
        VaultEnvelopeSupport.requireNonce(nonce, "nonceBase64");
        VaultEnvelopeSupport.requireAlgoVersion(request.algoVersion());
        VaultEnvelopeSupport.requirePayloadVersion(request.payloadVersion());
        return new DecodedEnvelope(ciphertext, nonce, request.algoVersion(), request.payloadVersion());
    }

    private static VaultCipherEnvelopeResponse toResponse(VaultItem item) {
        return new VaultCipherEnvelopeResponse(
                item.getId(),
                VaultEnvelopeSupport.encodeBase64(item.getCiphertext()),
                VaultEnvelopeSupport.encodeBase64(item.getNonce()),
                item.getAlgoVersion(),
                item.getPayloadVersion(),
                item.getVersion(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }

    private record DecodedEnvelope(byte[] ciphertext, byte[] nonce, int algoVersion, int payloadVersion) {
    }
}
