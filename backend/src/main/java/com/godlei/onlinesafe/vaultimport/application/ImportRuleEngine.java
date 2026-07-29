package com.godlei.onlinesafe.vaultimport.application;

import com.godlei.onlinesafe.vault.application.VaultItemService;
import com.godlei.onlinesafe.vault.web.VaultRecordResponse;
import com.godlei.onlinesafe.vaultimport.domain.ImportCandidateBucket;
import com.godlei.onlinesafe.vaultimport.domain.ImportIssueCode;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class ImportRuleEngine {

    private final VaultItemService vaultItemService;

    public ImportRuleEngine(VaultItemService vaultItemService) {
        this.vaultItemService = vaultItemService;
    }

    public List<ImportCandidate> classify(String ownerId, List<ImportCandidate> candidates, int thresholdPercent) {
        double threshold = thresholdPercent / 100.0;
        Set<String> existingKeys = buildExistingKeys(ownerId);
        for (ImportCandidate candidate : candidates) {
            if (candidate.getIssues().contains(ImportIssueCode.EMPTY_ROW)
                    && candidate.getBucket() == ImportCandidateBucket.SKIPPED) {
                continue;
            }
            List<ImportIssueCode> issues = new ArrayList<>();
            for (ImportIssueCode issue : candidate.getIssues()) {
                // 名称/平台非必填，不继承旧的缺失标记
                if (issue == ImportIssueCode.MISSING_NAME || issue == ImportIssueCode.MISSING_PLATFORM) {
                    continue;
                }
                if (issue != ImportIssueCode.LOW_CONFIDENCE
                        && issue != ImportIssueCode.DUPLICATE_SUSPECTED
                        && issue != ImportIssueCode.MISSING_ACCOUNT
                        && issue != ImportIssueCode.MISSING_PASSWORD) {
                    issues.add(issue);
                }
            }
            JsonNode payload = candidate.getPayload();
            String name = text(payload, "name");
            String platform = text(payload, "platform");
            String account = findSystemValue(payload, "account");
            String password = findSystemValue(payload, "password");

            // 仅账号、密码为必填；名称/平台缺失不阻断导入
            if (account.isBlank()) {
                issues.add(ImportIssueCode.MISSING_ACCOUNT);
            }
            if (password.isBlank()) {
                issues.add(ImportIssueCode.MISSING_PASSWORD);
            }
            if (!candidate.isForceReady() && candidate.getConfidence() < threshold) {
                issues.add(ImportIssueCode.LOW_CONFIDENCE);
            }
            boolean duplicate = (!account.isBlank() && existingKeys.contains("A:" + normalize(platform) + "|" + normalize(account)))
                    || existingKeys.contains("N:" + normalize(name) + "|" + normalize(platform));
            if (duplicate) {
                issues.add(ImportIssueCode.DUPLICATE_SUSPECTED);
                if (candidate.getDuplicateAction() == null) {
                    candidate.setDuplicateAction(ImportCandidate.DuplicateAction.SKIP);
                }
            } else if (candidate.getDuplicateAction() == ImportCandidate.DuplicateAction.SKIP) {
                candidate.setDuplicateAction(null);
            }
            if (candidate.isForceReady()) {
                // 用户已确认：清除审查类问题；账密仍缺失则继续拦
                issues.removeIf(code ->
                        code == ImportIssueCode.AI_OUTPUT_INVALID
                                || code == ImportIssueCode.AI_UNAVAILABLE
                                || code == ImportIssueCode.MANUAL_MAPPING_REQUIRED
                                || code == ImportIssueCode.LOW_CONFIDENCE
                                || code == ImportIssueCode.MISSING_NAME
                                || code == ImportIssueCode.MISSING_PLATFORM);
            }
            candidate.setIssues(dedupe(issues));
            candidate.setBucket(resolveBucket(candidate));
        }
        return candidates;
    }

    private ImportCandidateBucket resolveBucket(ImportCandidate candidate) {
        if (candidate.getIssues().contains(ImportIssueCode.EMPTY_ROW)) {
            return ImportCandidateBucket.SKIPPED;
        }
        if (candidate.getDuplicateAction() == ImportCandidate.DuplicateAction.SKIP
                && candidate.getIssues().contains(ImportIssueCode.DUPLICATE_SUSPECTED)) {
            return ImportCandidateBucket.SKIPPED;
        }
        if (candidate.isForceReady()) {
            boolean missingSecrets = candidate.getIssues().contains(ImportIssueCode.MISSING_ACCOUNT)
                    || candidate.getIssues().contains(ImportIssueCode.MISSING_PASSWORD);
            if (missingSecrets) {
                return ImportCandidateBucket.NEEDS_REVIEW;
            }
            boolean unresolvedDuplicate = candidate.getIssues().contains(ImportIssueCode.DUPLICATE_SUSPECTED)
                    && candidate.getDuplicateAction() != ImportCandidate.DuplicateAction.CREATE;
            return unresolvedDuplicate ? ImportCandidateBucket.NEEDS_REVIEW : ImportCandidateBucket.READY;
        }
        boolean needsReview = candidate.getIssues().stream().anyMatch(code ->
                code == ImportIssueCode.MISSING_ACCOUNT
                        || code == ImportIssueCode.MISSING_PASSWORD
                        || code == ImportIssueCode.AI_OUTPUT_INVALID
                        || code == ImportIssueCode.AI_UNAVAILABLE
                        || code == ImportIssueCode.MANUAL_MAPPING_REQUIRED
                        || code == ImportIssueCode.LOW_CONFIDENCE
                        || (code == ImportIssueCode.DUPLICATE_SUSPECTED
                        && candidate.getDuplicateAction() != ImportCandidate.DuplicateAction.CREATE)
        );
        return needsReview ? ImportCandidateBucket.NEEDS_REVIEW : ImportCandidateBucket.READY;
    }

    private Set<String> buildExistingKeys(String ownerId) {
        Set<String> keys = new HashSet<>();
        for (VaultRecordResponse item : vaultItemService.list(ownerId)) {
            JsonNode payload = item.payload();
            String name = text(payload, "name");
            String platform = text(payload, "platform");
            String account = findSystemValue(payload, "account");
            keys.add("N:" + normalize(name) + "|" + normalize(platform));
            if (!account.isBlank()) {
                keys.add("A:" + normalize(platform) + "|" + normalize(account));
            }
        }
        return keys;
    }

    private static String findSystemValue(JsonNode payload, String systemKey) {
        JsonNode fields = payload.path("fields");
        if (!fields.isArray()) {
            return "";
        }
        for (JsonNode field : fields) {
            if (systemKey.equals(field.path("systemKey").asText())) {
                return field.path("value").asText("").trim();
            }
        }
        return "";
    }

    private static List<ImportIssueCode> dedupe(List<ImportIssueCode> issues) {
        List<ImportIssueCode> result = new ArrayList<>();
        for (ImportIssueCode issue : issues) {
            if (!result.contains(issue)) {
                result.add(issue);
            }
        }
        return result;
    }

    private static String text(JsonNode payload, String field) {
        if (payload == null || payload.get(field) == null || payload.get(field).isNull()) {
            return "";
        }
        return payload.get(field).asText("").trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
