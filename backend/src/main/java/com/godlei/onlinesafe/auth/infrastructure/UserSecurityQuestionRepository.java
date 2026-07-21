package com.godlei.onlinesafe.auth.infrastructure;

import com.godlei.onlinesafe.auth.domain.UserSecurityQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserSecurityQuestionRepository extends JpaRepository<UserSecurityQuestion, String> {

    List<UserSecurityQuestion> findByOwnerIdOrderBySortOrderAsc(String ownerId);

    void deleteByOwnerId(String ownerId);

    long countByOwnerId(String ownerId);
}
