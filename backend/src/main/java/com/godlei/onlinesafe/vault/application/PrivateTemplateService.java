package com.godlei.onlinesafe.vault.application;

import com.godlei.onlinesafe.vault.domain.PrivateTemplate;
import com.godlei.onlinesafe.vault.infrastructure.PrivateTemplateRepository;
import com.godlei.onlinesafe.vault.web.VaultCipherEnvelopeRequest;
import com.godlei.onlinesafe.vault.web.VaultCipherEnvelopeResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PrivateTemplateService {

    private final PrivateTemplateRepository repository;

    public PrivateTemplateService(PrivateTemplateRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<VaultCipherEnvelopeResponse> list(String ownerId) {
        return repository.findByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(ownerId).stream()
                .map(PrivateTemplateService::toResponse)
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
            throw new InvalidVaultEnvelopeException("TEMPLATE_ID_CONFLICT", "模板 ID 已存在");
        }
        DecodedEnvelope envelope = decode(request);
        PrivateTemplate saved = repository.saveAndFlush(PrivateTemplate.create(
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
        PrivateTemplate template = requireOwned(ownerId, id);
        if (request.revision() != template.getVersion()) {
            throw new VaultRevisionConflictException();
        }
        DecodedEnvelope envelope = decode(request);
        template.replaceCiphertext(
                envelope.ciphertext(),
                envelope.nonce(),
                envelope.algoVersion(),
                envelope.payloadVersion()
        );
        return toResponse(repository.saveAndFlush(template));
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

    private static DecodedEnvelope decode(VaultCipherEnvelopeRequest request) {
        byte[] ciphertext = VaultEnvelopeSupport.decodeBase64(request.ciphertextBase64(), "ciphertextBase64");
        byte[] nonce = VaultEnvelopeSupport.decodeBase64(request.nonceBase64(), "nonceBase64");
        VaultEnvelopeSupport.requireNonEmpty(ciphertext, "ciphertextBase64");
        VaultEnvelopeSupport.requireNonce(nonce, "nonceBase64");
        VaultEnvelopeSupport.requireAlgoVersion(request.algoVersion());
        VaultEnvelopeSupport.requirePayloadVersion(request.payloadVersion());
        return new DecodedEnvelope(ciphertext, nonce, request.algoVersion(), request.payloadVersion());
    }

    private static VaultCipherEnvelopeResponse toResponse(PrivateTemplate template) {
        return new VaultCipherEnvelopeResponse(
                template.getId(),
                VaultEnvelopeSupport.encodeBase64(template.getCiphertext()),
                VaultEnvelopeSupport.encodeBase64(template.getNonce()),
                template.getAlgoVersion(),
                template.getPayloadVersion(),
                template.getVersion(),
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }

    private record DecodedEnvelope(byte[] ciphertext, byte[] nonce, int algoVersion, int payloadVersion) {
    }
}
