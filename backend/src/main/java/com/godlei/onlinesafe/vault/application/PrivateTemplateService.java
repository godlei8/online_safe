package com.godlei.onlinesafe.vault.application;

import com.godlei.onlinesafe.vault.domain.PrivateTemplate;
import com.godlei.onlinesafe.vault.infrastructure.PrivateTemplateRepository;
import com.godlei.onlinesafe.vault.web.VaultRecordRequest;
import com.godlei.onlinesafe.vault.web.VaultRecordResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.List;

@Service
public class PrivateTemplateService {

    public static final String ENTITY_TYPE = "TEMPLATE";

    private final PrivateTemplateRepository repository;
    private final VaultPayloadCipher cipher;

    public PrivateTemplateService(PrivateTemplateRepository repository, VaultPayloadCipher cipher) {
        this.repository = repository;
        this.cipher = cipher;
    }

    @Transactional(readOnly = true)
    public List<VaultRecordResponse> list(String ownerId) {
        return repository.findByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(ownerId).stream()
                .map(template -> toResponse(ownerId, template))
                .toList();
    }

    @Transactional(readOnly = true)
    public VaultRecordResponse get(String ownerId, String id) {
        return toResponse(ownerId, requireOwned(ownerId, id));
    }

    @Transactional
    public VaultRecordResponse create(String ownerId, VaultRecordRequest request) {
        VaultRecordSupport.requireUuid(request.id());
        VaultRecordSupport.requireTemplatePayload(request.payload());
        if (repository.existsById(request.id())) {
            throw new InvalidVaultEnvelopeException("TEMPLATE_ID_CONFLICT", "模板 ID 已存在");
        }
        VaultPayloadCipher.SealedPayload sealed = cipher.encrypt(
                ownerId,
                ENTITY_TYPE,
                request.id().trim(),
                request.payload()
        );
        PrivateTemplate saved = repository.saveAndFlush(PrivateTemplate.create(
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
        PrivateTemplate template = requireOwned(ownerId, id);
        if (request.revision() != template.getVersion()) {
            throw new VaultRevisionConflictException();
        }
        VaultRecordSupport.requireTemplatePayload(request.payload());
        VaultPayloadCipher.SealedPayload sealed = cipher.encrypt(
                ownerId,
                ENTITY_TYPE,
                id,
                request.payload()
        );
        template.replaceCiphertext(
                sealed.ciphertext(),
                sealed.nonce(),
                sealed.algoVersion(),
                sealed.payloadVersion(),
                sealed.keyId()
        );
        return toResponse(ownerId, repository.saveAndFlush(template));
    }

    @Transactional
    public void delete(String ownerId, String id) {
        PrivateTemplate template = requireOwned(ownerId, id);
        template.softDelete();
        repository.saveAndFlush(template);
    }

    private PrivateTemplate requireOwned(String ownerId, String id) {
        return repository.findByIdAndOwnerIdAndDeletedAtIsNull(id, ownerId)
                .orElseThrow(PrivateTemplateNotFoundException::new);
    }

    private VaultRecordResponse toResponse(String ownerId, PrivateTemplate template) {
        JsonNode payload = cipher.decrypt(
                ownerId,
                ENTITY_TYPE,
                template.getId(),
                template.getCiphertext(),
                template.getNonce(),
                template.getAlgoVersion(),
                template.getPayloadVersion(),
                template.getKeyId()
        );
        return new VaultRecordResponse(
                template.getId(),
                payload,
                template.getVersion(),
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }
}
