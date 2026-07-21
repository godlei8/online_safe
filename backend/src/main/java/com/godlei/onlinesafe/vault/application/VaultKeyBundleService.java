package com.godlei.onlinesafe.vault.application;

import com.godlei.onlinesafe.vault.domain.VaultKeyBundle;
import com.godlei.onlinesafe.vault.infrastructure.VaultKeyBundleRepository;
import com.godlei.onlinesafe.vault.web.VaultKeyBundleReplaceRequest;
import com.godlei.onlinesafe.vault.web.VaultKeyBundleRequest;
import com.godlei.onlinesafe.vault.web.VaultKeyBundleResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VaultKeyBundleService {

    private final VaultKeyBundleRepository repository;

    public VaultKeyBundleService(VaultKeyBundleRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public VaultKeyBundleResponse get(String ownerId) {
        VaultKeyBundle bundle = repository.findByOwnerId(ownerId)
                .orElseThrow(VaultNotInitializedException::new);
        return toResponse(bundle);
    }

    @Transactional
    public VaultKeyBundleResponse create(String ownerId, VaultKeyBundleRequest request) {
        if (repository.existsByOwnerId(ownerId)) {
            throw new VaultAlreadyInitializedException();
        }

        byte[] kdfSalt = VaultEnvelopeSupport.decodeBase64(request.kdfSaltBase64(), "kdfSaltBase64");
        VaultEnvelopeSupport.requireNonEmpty(kdfSalt, "kdfSaltBase64");
        if (request.kdfOpsLimit() < 1 || request.kdfMemLimit() < 1) {
            throw new InvalidVaultEnvelopeException("VALIDATION_FAILED", "KDF 参数无效");
        }

        byte[] wrappedMaster = VaultEnvelopeSupport.decodeBase64(request.wrappedDekMasterBase64(), "wrappedDekMasterBase64");
        byte[] wrappedMasterNonce = VaultEnvelopeSupport.decodeBase64(
                request.wrappedDekMasterNonceBase64(),
                "wrappedDekMasterNonceBase64"
        );
        byte[] wrappedRecovery = VaultEnvelopeSupport.decodeBase64(
                request.wrappedDekRecoveryBase64(),
                "wrappedDekRecoveryBase64"
        );
        byte[] wrappedRecoveryNonce = VaultEnvelopeSupport.decodeBase64(
                request.wrappedDekRecoveryNonceBase64(),
                "wrappedDekRecoveryNonceBase64"
        );

        VaultEnvelopeSupport.requireNonEmpty(wrappedMaster, "wrappedDekMasterBase64");
        VaultEnvelopeSupport.requireNonEmpty(wrappedRecovery, "wrappedDekRecoveryBase64");
        VaultEnvelopeSupport.requireNonce(wrappedMasterNonce, "wrappedDekMasterNonceBase64");
        VaultEnvelopeSupport.requireNonce(wrappedRecoveryNonce, "wrappedDekRecoveryNonceBase64");
        VaultEnvelopeSupport.requireAlgoVersion(request.algoVersion());

        VaultKeyBundle saved = repository.saveAndFlush(VaultKeyBundle.create(
                ownerId,
                kdfSalt,
                request.kdfOpsLimit(),
                request.kdfMemLimit(),
                wrappedMaster,
                wrappedMasterNonce,
                wrappedRecovery,
                wrappedRecoveryNonce,
                request.algoVersion()
        ));
        return toResponse(saved);
    }

    @Transactional
    public VaultKeyBundleResponse replace(String ownerId, VaultKeyBundleReplaceRequest request) {
        VaultKeyBundle bundle = repository.findByOwnerId(ownerId)
                .orElseThrow(VaultNotInitializedException::new);
        if (bundle.getVersion() != request.revision()) {
            throw new VaultRevisionConflictException();
        }

        byte[] kdfSalt = VaultEnvelopeSupport.decodeBase64(request.kdfSaltBase64(), "kdfSaltBase64");
        VaultEnvelopeSupport.requireNonEmpty(kdfSalt, "kdfSaltBase64");
        if (request.kdfOpsLimit() < 1 || request.kdfMemLimit() < 1) {
            throw new InvalidVaultEnvelopeException("VALIDATION_FAILED", "KDF 参数无效");
        }

        byte[] wrappedMaster = VaultEnvelopeSupport.decodeBase64(request.wrappedDekMasterBase64(), "wrappedDekMasterBase64");
        byte[] wrappedMasterNonce = VaultEnvelopeSupport.decodeBase64(
                request.wrappedDekMasterNonceBase64(),
                "wrappedDekMasterNonceBase64"
        );
        byte[] wrappedRecovery = VaultEnvelopeSupport.decodeBase64(
                request.wrappedDekRecoveryBase64(),
                "wrappedDekRecoveryBase64"
        );
        byte[] wrappedRecoveryNonce = VaultEnvelopeSupport.decodeBase64(
                request.wrappedDekRecoveryNonceBase64(),
                "wrappedDekRecoveryNonceBase64"
        );

        VaultEnvelopeSupport.requireNonEmpty(wrappedMaster, "wrappedDekMasterBase64");
        VaultEnvelopeSupport.requireNonEmpty(wrappedRecovery, "wrappedDekRecoveryBase64");
        VaultEnvelopeSupport.requireNonce(wrappedMasterNonce, "wrappedDekMasterNonceBase64");
        VaultEnvelopeSupport.requireNonce(wrappedRecoveryNonce, "wrappedDekRecoveryNonceBase64");
        VaultEnvelopeSupport.requireAlgoVersion(request.algoVersion());

        bundle.replaceWraps(
                kdfSalt,
                request.kdfOpsLimit(),
                request.kdfMemLimit(),
                wrappedMaster,
                wrappedMasterNonce,
                wrappedRecovery,
                wrappedRecoveryNonce,
                request.algoVersion()
        );
        return toResponse(repository.saveAndFlush(bundle));
    }

    private static VaultKeyBundleResponse toResponse(VaultKeyBundle bundle) {
        return new VaultKeyBundleResponse(
                VaultEnvelopeSupport.encodeBase64(bundle.getKdfSalt()),
                bundle.getKdfOpsLimit(),
                bundle.getKdfMemLimit(),
                VaultEnvelopeSupport.encodeBase64(bundle.getWrappedDekMaster()),
                VaultEnvelopeSupport.encodeBase64(bundle.getWrappedDekMasterNonce()),
                VaultEnvelopeSupport.encodeBase64(bundle.getWrappedDekRecovery()),
                VaultEnvelopeSupport.encodeBase64(bundle.getWrappedDekRecoveryNonce()),
                bundle.getAlgoVersion(),
                bundle.getVersion(),
                bundle.getCreatedAt(),
                bundle.getUpdatedAt()
        );
    }
}
