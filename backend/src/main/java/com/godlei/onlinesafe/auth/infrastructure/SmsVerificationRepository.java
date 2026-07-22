package com.godlei.onlinesafe.auth.infrastructure;

import com.godlei.onlinesafe.auth.domain.SmsPurpose;
import com.godlei.onlinesafe.auth.domain.SmsVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SmsVerificationRepository extends JpaRepository<SmsVerification, String> {

    Optional<SmsVerification> findFirstByPhoneAndPurposeOrderByCreatedAtDesc(String phone, SmsPurpose purpose);

    long countByPhoneAndPurposeAndCreatedAtAfter(String phone, SmsPurpose purpose, Instant after);

    List<SmsVerification> findByPhoneAndPurposeAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            String phone,
            SmsPurpose purpose,
            Instant now
    );
}
