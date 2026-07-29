package com.godlei.onlinesafe.vaultimport.application;

import com.godlei.onlinesafe.vault.application.VaultPayloadCipher;
import com.godlei.onlinesafe.vaultimport.domain.VaultImportSession;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

@Component
public class ImportCandidateSealService {

    public static final String ENTITY_TYPE = "IMPORT_SESSION";

    private final VaultPayloadCipher cipher;
    private final ObjectMapper objectMapper;

    public ImportCandidateSealService(VaultPayloadCipher cipher, ObjectMapper objectMapper) {
        this.cipher = cipher;
        this.objectMapper = objectMapper;
    }

    public void seal(VaultImportSession session, List<ImportCandidate> candidates) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode items = root.putArray("candidates");
        for (ImportCandidate candidate : candidates) {
            items.add(objectMapper.valueToTree(candidate));
        }
        VaultPayloadCipher.SealedPayload sealed = cipher.encrypt(
                session.getOwnerId(),
                ENTITY_TYPE,
                session.getId(),
                root
        );
        session.storeCandidates(
                sealed.ciphertext(),
                sealed.nonce(),
                sealed.algoVersion(),
                sealed.keyId()
        );
    }

    public List<ImportCandidate> unseal(VaultImportSession session) {
        if (!session.hasCandidates()) {
            return List.of();
        }
        JsonNode root = cipher.decrypt(
                session.getOwnerId(),
                ENTITY_TYPE,
                session.getId(),
                session.getCandidatesCiphertext(),
                session.getCandidatesNonce(),
                session.getCandidatesAlgoVersion() == null ? VaultPayloadCipher.ALGO_VERSION : session.getCandidatesAlgoVersion(),
                VaultPayloadCipher.PAYLOAD_VERSION,
                session.getCandidatesKeyId() == null ? cipher.currentKeyId() : session.getCandidatesKeyId()
        );
        JsonNode items = root.path("candidates");
        List<ImportCandidate> candidates = new ArrayList<>();
        if (items.isArray()) {
            for (JsonNode item : items) {
                candidates.add(objectMapper.convertValue(item, ImportCandidate.class));
            }
        }
        return candidates;
    }
}
