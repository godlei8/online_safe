package com.godlei.onlinesafe.admin.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通过 JDBC 统计/删除个人用户 Spring Session。
 * 兼容 MySQL（SPRING_SESSION）与 H2 DATABASE_TO_LOWER（spring_session）。
 * 表尚未就绪时安全降级为 0，避免拖垮用户列表。
 */
@Repository
public class UserSessionRepository {

    private static final Logger log = LoggerFactory.getLogger(UserSessionRepository.class);

    private static final List<String[]> CANDIDATES = List.of(
            new String[]{"SPRING_SESSION", "PRINCIPAL_NAME"},
            new String[]{"spring_session", "principal_name"}
    );

    private final JdbcTemplate jdbcTemplate;
    private volatile String[] identifiers;

    public UserSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int countByPrincipalName(String principalName) {
        String[] id = resolveIdentifiers();
        if (id == null) {
            return 0;
        }
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + id[0] + " WHERE " + id[1] + " = ?",
                    Integer.class,
                    principalName
            );
            return count == null ? 0 : count;
        } catch (DataAccessException ex) {
            log.debug("统计会话失败，按 0 处理：{}", ex.getMessage());
            return 0;
        }
    }

    public Map<String, Integer> countByPrincipalNames(Collection<String> principalNames) {
        if (principalNames == null || principalNames.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Integer> result = new HashMap<>();
        for (String principalName : principalNames) {
            result.put(principalName, countByPrincipalName(principalName));
        }
        return result;
    }

    public int deleteByPrincipalName(String principalName) {
        String[] id = resolveIdentifiers();
        if (id == null) {
            return 0;
        }
        try {
            return jdbcTemplate.update(
                    "DELETE FROM " + id[0] + " WHERE " + id[1] + " = ?",
                    principalName
            );
        } catch (DataAccessException ex) {
            log.debug("删除会话失败，按 0 处理：{}", ex.getMessage());
            return 0;
        }
    }

    private String[] resolveIdentifiers() {
        String[] cached = identifiers;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (identifiers != null) {
                return identifiers;
            }
            for (String[] candidate : CANDIDATES) {
                try {
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM " + candidate[0] + " WHERE 1=0",
                            Integer.class
                    );
                    identifiers = candidate;
                    return identifiers;
                } catch (DataAccessException ignored) {
                    // try next
                }
            }
            log.warn("未找到 Spring Session 表，用户会话统计/失效将暂时降级为空操作");
            return null;
        }
    }
}
