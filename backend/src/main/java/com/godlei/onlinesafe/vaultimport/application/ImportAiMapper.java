package com.godlei.onlinesafe.vaultimport.application;

import com.godlei.onlinesafe.vaultimport.domain.ImportCandidateBucket;
import com.godlei.onlinesafe.vaultimport.domain.ImportIssueCode;
import com.godlei.onlinesafe.vaultimport.domain.ImportMode;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ImportAiMapper {

    private static final int BATCH_SIZE = 30;
    private static final Pattern KV_LINE = Pattern.compile("^([^:：\\n|]{1,40})\\s*[：:]\\s*(.+)$");

    private static final String SYSTEM_PROMPT = """
            你是 Online Safe 账密导入助手。根据用户提供的表格行或文本块，输出 JSON：
            {"items":[{"rowIndex":1,"confidence":0.0到1.0,"skip":false,"name":"","platform":"","channel":"","channelUrl":"","account":"","password":"","notes":"","expiresAt":null,"extraFields":[{"name":"","type":"TEXT|PASSWORD|EMAIL|URL|PHONE","value":""}]}]}
            规则：
            1. 只输出 JSON；未知列放入 extraFields；不要在其它字段复述完整密码；空行 skip=true。
            2. 账号与密码是必填：尽量从原文抽出；抽不出则对应字段留空字符串。
            3. 名称、平台、渠道、网址等为选填：可从标题、品牌名、网址适度推断；没有也不要编造敏感内容。
            4. 网址写入 channelUrl；平台可用品牌名（如 Notion、谷歌），不要把完整 URL 当作 platform。
            """;

    private final OpenAiCompatibleChatClient chatClient;
    private final ObjectMapper objectMapper;

    public ImportAiMapper(OpenAiCompatibleChatClient chatClient, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    public record MapResult(List<ImportCandidate> candidates, boolean degraded, String modelName, String provider) {
    }

    /**
     * @param mode              FAST=本地规则；AI=调用大模型
     * @param onBatchProgress 回调参数为 (已处理行数, 总行数)，可为 null
     */
    public MapResult map(ImportDocument document, ImportMode mode, BiConsumer<Integer, Integer> onBatchProgress) {
        ImportMode effective = mode == null ? ImportMode.FAST : mode;
        int total = document.rows().size();
        if (total == 0) {
            if (onBatchProgress != null) {
                onBatchProgress.accept(0, 0);
            }
            return new MapResult(List.of(), true, null, null);
        }
        if (effective == ImportMode.FAST) {
            return mapFast(document, onBatchProgress);
        }
        return mapWithAi(document, onBatchProgress);
    }

    public MapResult map(ImportDocument document, BiConsumer<Integer, Integer> onBatchProgress) {
        return map(document, ImportMode.AI, onBatchProgress);
    }

    public MapResult map(ImportDocument document) {
        return map(document, ImportMode.AI, null);
    }

    /** 快速导入：始终本地规则，不调用大模型；按行决定确定性映射或需核对 */
    private MapResult mapFast(ImportDocument document, BiConsumer<Integer, Integer> onBatchProgress) {
        int total = document.rows().size();
        if (onBatchProgress != null) {
            onBatchProgress.accept(0, total);
        }
        List<ImportCandidate> candidates = new ArrayList<>();
        for (ImportDocument.RawRow row : document.rows()) {
            // 按行判断：能抽出名称/平台/账号/密码任一组合则走确定性映射，避免首行碎片拖垮整文件
            if (rowHitCount(row) >= 1) {
                candidates.add(deterministicRow(row));
            } else {
                candidates.add(manualFallbackRow(row, false));
            }
        }
        if (onBatchProgress != null) {
            onBatchProgress.accept(total, total);
        }
        return new MapResult(candidates, false, "本地规则", "fast");
    }

    /** AI 智能识别：已配置则调模型；未配置则降级本地并标记需核对 */
    private MapResult mapWithAi(ImportDocument document, BiConsumer<Integer, Integer> onBatchProgress) {
        int total = document.rows().size();
        if (!chatClient.isConfigured()) {
            if (onBatchProgress != null) {
                onBatchProgress.accept(0, total);
            }
            return new MapResult(manualFallback(document, true), true, null, null);
        }
        List<ImportCandidate> all = new ArrayList<>();
        List<ImportDocument.RawRow> rows = document.rows();
        for (int start = 0; start < total; start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, total);
            if (onBatchProgress != null) {
                onBatchProgress.accept(start, total);
            }
            List<ImportDocument.RawRow> batch = rows.subList(start, end);
            all.addAll(mapBatch(batch));
            if (onBatchProgress != null) {
                onBatchProgress.accept(end, total);
            }
        }
        return new MapResult(all, false, chatClient.modelName(), chatClient.providerHint());
    }

    private int rowHitCount(ImportDocument.RawRow row) {
        Map<String, String> cells = enrichCells(row);
        int hits = 0;
        if (first(cells, "名称", "标题", "name", "title") != null) {
            hits++;
        }
        if (first(cells, "平台", "网站", "platform", "site") != null) {
            hits++;
        }
        if (first(cells, "账号", "用户名", "邮箱", "account", "username", "email") != null) {
            hits++;
        }
        if (first(cells, "密码", "口令", "password") != null) {
            hits++;
        }
        return hits;
    }

    private ImportCandidate deterministicRow(ImportDocument.RawRow row) {
        ImportCandidate candidate = new ImportCandidate();
        candidate.setId(UUID.randomUUID().toString());
        candidate.setRowIndex(row.rowIndex());
        candidate.setPayload(heuristicPayload(row));
        candidate.setConfidence(scoreDeterministic(row));
        candidate.setBucket(ImportCandidateBucket.NEEDS_REVIEW);
        candidate.setSourceHint(row.rawText());
        return candidate;
    }

    private double scoreDeterministic(ImportDocument.RawRow row) {
        int score = rowHitCount(row);
        if (score >= 3) {
            return 0.92;
        }
        if (score == 2) {
            return 0.84;
        }
        return 0.72;
    }

    private Map<String, String> enrichCells(ImportDocument.RawRow row) {
        Map<String, String> cells = new LinkedHashMap<>();
        if (row.cells() != null) {
            cells.putAll(row.cells());
        }
        mergeParsedLabels(cells, first(cells, "原文", "备注", "notes"));
        mergeParsedLabels(cells, row.rawText());
        if (first(cells, "名称", "标题", "name", "title") == null) {
            String inferred = inferNameFromRaw(row.rawText());
            if (inferred != null) {
                cells.put("名称", inferred);
            }
        }
        if (first(cells, "平台", "网站", "站点", "platform", "site") == null) {
            String platform = inferPlatform(
                    first(cells, "名称", "标题", "name", "title"),
                    joinTexts(
                            first(cells, "原文"),
                            row.rawText(),
                            first(cells, "渠道网址", "网址", "url", "website")
                    )
            );
            if (platform != null) {
                cells.put("平台", platform);
            }
        }
        return cells;
    }

    private List<ImportCandidate> mapBatch(List<ImportDocument.RawRow> batch) {
        ObjectNode user = objectMapper.createObjectNode();
        ArrayNode rowsNode = user.putArray("rows");
        for (ImportDocument.RawRow row : batch) {
            ObjectNode item = rowsNode.addObject();
            item.put("rowIndex", row.rowIndex());
            if (row.cells() != null) {
                ObjectNode cells = item.putObject("cells");
                for (Map.Entry<String, String> entry : row.cells().entrySet()) {
                    cells.put(entry.getKey(), entry.getValue());
                }
            }
            if (row.rawText() != null) {
                item.put("rawText", row.rawText());
            }
        }
        try {
            String content = chatClient.chatJson(SYSTEM_PROMPT, objectMapper.writeValueAsString(user));
            JsonNode parsed = objectMapper.readTree(extractJsonObject(content));
            JsonNode items = parsed.path("items");
            if (!items.isArray()) {
                return invalidBatch(batch);
            }
            List<ImportCandidate> candidates = new ArrayList<>();
            for (JsonNode item : items) {
                int rowIndex = item.path("rowIndex").asInt(0);
                ImportDocument.RawRow source = batch.stream()
                        .filter(row -> row.rowIndex() == rowIndex)
                        .findFirst()
                        .orElse(batch.size() == 1 ? batch.getFirst() : null);
                candidates.add(fromAiItem(item, source));
            }
            if (candidates.size() < batch.size()) {
                return alignAiCandidates(batch, candidates);
            }
            return candidates.isEmpty() ? invalidBatch(batch) : candidates;
        } catch (VaultImportException exception) {
            throw exception;
        } catch (Exception exception) {
            return invalidBatch(batch);
        }
    }

    private List<ImportCandidate> alignAiCandidates(List<ImportDocument.RawRow> batch, List<ImportCandidate> mapped) {
        Map<Integer, ImportCandidate> byRow = new LinkedHashMap<>();
        for (ImportCandidate candidate : mapped) {
            byRow.putIfAbsent(candidate.getRowIndex(), candidate);
        }
        List<ImportCandidate> aligned = new ArrayList<>();
        for (ImportDocument.RawRow row : batch) {
            ImportCandidate candidate = byRow.get(row.rowIndex());
            if (candidate == null) {
                aligned.add(manualFallbackRow(row, false));
            } else {
                aligned.add(candidate);
            }
        }
        return aligned;
    }

    private ImportCandidate fromAiItem(JsonNode item, ImportDocument.RawRow source) {
        ImportCandidate candidate = new ImportCandidate();
        candidate.setId(UUID.randomUUID().toString());
        candidate.setRowIndex(item.path("rowIndex").asInt(0));
        candidate.setConfidence(clamp(item.path("confidence").asDouble(0.5)));
        if (item.path("skip").asBoolean(false)) {
            candidate.setBucket(ImportCandidateBucket.SKIPPED);
            candidate.setIssues(List.of(ImportIssueCode.EMPTY_ROW));
            candidate.setPayload(emptyPayload("未命名", ""));
            return candidate;
        }
        candidate.setPayload(toPayload(item));
        candidate.setBucket(ImportCandidateBucket.NEEDS_REVIEW);
        if (source != null) {
            candidate.setSourceHint(source.rawText());
        }
        return candidate;
    }

    private List<ImportCandidate> manualFallback(ImportDocument document, boolean aiUnavailable) {
        List<ImportCandidate> candidates = new ArrayList<>();
        for (ImportDocument.RawRow row : document.rows()) {
            candidates.add(manualFallbackRow(row, aiUnavailable));
        }
        return candidates;
    }

    private ImportCandidate manualFallbackRow(ImportDocument.RawRow row, boolean aiUnavailable) {
        ImportCandidate candidate = new ImportCandidate();
        candidate.setId(UUID.randomUUID().toString());
        candidate.setRowIndex(row.rowIndex());
        candidate.setConfidence(aiUnavailable ? 0.4 : 0.55);
        List<ImportIssueCode> issues = new ArrayList<>();
        if (aiUnavailable) {
            issues.add(ImportIssueCode.AI_UNAVAILABLE);
        }
        issues.add(ImportIssueCode.MANUAL_MAPPING_REQUIRED);
        candidate.setIssues(issues);
        candidate.setBucket(ImportCandidateBucket.NEEDS_REVIEW);
        candidate.setPayload(heuristicPayload(row));
        candidate.setSourceHint(row.rawText());
        return candidate;
    }

    private List<ImportCandidate> invalidBatch(List<ImportDocument.RawRow> batch) {
        List<ImportCandidate> candidates = new ArrayList<>();
        for (ImportDocument.RawRow row : batch) {
            ImportCandidate candidate = new ImportCandidate();
            candidate.setId(UUID.randomUUID().toString());
            candidate.setRowIndex(row.rowIndex());
            candidate.setConfidence(0.2);
            candidate.setIssues(List.of(ImportIssueCode.AI_OUTPUT_INVALID));
            candidate.setBucket(ImportCandidateBucket.NEEDS_REVIEW);
            candidate.setPayload(heuristicPayload(row));
            candidates.add(candidate);
        }
        return candidates;
    }

    private JsonNode heuristicPayload(ImportDocument.RawRow row) {
        Map<String, String> cells = new LinkedHashMap<>();
        if (row.cells() != null) {
            cells.putAll(row.cells());
        }
        // 单元格缺账号/密码时，从原文键值行补抽（适配自由格式 md/txt）
        if (first(cells, "账号", "用户名", "邮箱", "account", "username", "email") == null
                || first(cells, "密码", "口令", "password") == null) {
            mergeParsedLabels(cells, first(cells, "原文", "备注", "notes"));
            mergeParsedLabels(cells, row.rawText());
        }

        String name = first(cells, "名称", "标题", "name", "title");
        if (name == null) {
            name = inferNameFromRaw(row.rawText());
        }
        String channelUrl = first(cells, "渠道网址", "网址", "url", "website");
        String platform = first(cells, "平台", "网站", "站点", "platform", "site");
        if (platform == null) {
            platform = inferPlatform(name, joinTexts(first(cells, "原文"), row.rawText(), channelUrl));
        }
        String channel = first(cells, "渠道", "来源", "channel");
        String account = first(cells, "账号", "用户名", "邮箱", "account", "username", "email");
        String password = first(cells, "密码", "口令", "password");
        String notes = first(cells, "备注", "notes");
        if (notes == null && account == null && password == null) {
            notes = first(cells, "原文");
        }
        ObjectNode payload = emptyPayload(
                blankTo(name, "未命名记录"),
                platform == null ? "" : platform
        );
        payload.put("channel", channel == null ? "" : channel);
        payload.put("channelUrl", channelUrl == null ? "" : channelUrl);
        payload.put("notes", notes == null ? "" : notes);
        ArrayNode fields = (ArrayNode) payload.get("fields");
        setField(fields, "account", "账号", "TEXT", account == null ? "" : account, false);
        setField(fields, "password", "密码", "PASSWORD", password == null ? "" : password, true);
        for (Map.Entry<String, String> entry : cells.entrySet()) {
            String key = entry.getKey();
            if (isReserved(key) || entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            String type = guessExtraType(key);
            ObjectNode field = fields.addObject();
            field.put("id", UUID.randomUUID().toString());
            field.put("name", key);
            field.put("type", type);
            field.put("value", entry.getValue());
            field.put("required", false);
            field.put("sensitive", "PASSWORD".equals(type));
            field.put("copyable", true);
            field.put("hint", "");
            field.put("order", fields.size() - 1);
        }
        return payload;
    }

    private static void mergeParsedLabels(Map<String, String> cells, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        for (String rawLine : text.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            String line = rawLine.trim();
            if (line.startsWith("- ") || line.startsWith("* ")) {
                line = line.substring(2).trim();
            }
            Matcher matcher = KV_LINE.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String key = matcher.group(1).trim();
            String value = matcher.group(2).trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                cells.putIfAbsent(key, value);
            }
        }
    }

    private static String inferNameFromRaw(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return null;
        }
        for (String rawLine : rawText.replace("\r\n", "\n").split("\n")) {
            String line = rawLine.trim();
            if (line.startsWith("## ")) {
                return line.substring(3).trim();
            }
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (KV_LINE.matcher(line).matches()) {
                continue;
            }
            return line;
        }
        return null;
    }

    private static String joinTexts(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(part.trim());
        }
        return builder.toString();
    }

    /** 选填增强：有明确线索时补平台，不影响「非必填」规则 */
    private static String inferPlatform(String name, String fallbackText) {
        String source = joinTexts(name, fallbackText).toLowerCase(Locale.ROOT);
        if (source.isBlank()) {
            return null;
        }
        if (source.contains("谷歌") || source.contains("google") || source.contains("gmail")) {
            return "谷歌";
        }
        if (source.contains("chatgpt") || source.contains("openai")) {
            return "ChatGPT";
        }
        if (source.contains("claude")) {
            return "Claude";
        }
        if (source.contains("github")) {
            return "GitHub";
        }
        if (source.contains("notion")) {
            return "Notion";
        }
        if (source.contains("阿里云") || source.contains("aliyun")) {
            return "阿里云";
        }
        if (source.contains("steam")) {
            return "Steam";
        }
        if (source.contains("docker")) {
            return "Docker";
        }
        if (source.contains("微信") || source.contains("wechat")) {
            return "微信";
        }
        if (source.contains("网易") || source.contains("163.com")) {
            return "网易";
        }
        Matcher matcher = Pattern.compile("(?:https?://)?(?:www\\.)?([a-z0-9-]+)\\.(?:com|cn|io|net|org|so|app)(?:[/?#]|$)")
                .matcher(source);
        if (matcher.find() && (source.contains("http://") || source.contains("https://") || source.contains("www."))) {
            String host = matcher.group(1);
            return switch (host) {
                case "notion" -> "Notion";
                case "github" -> "GitHub";
                case "google", "gmail" -> "谷歌";
                default -> Character.toUpperCase(host.charAt(0)) + host.substring(1);
            };
        }
        return null;
    }

    private static String guessExtraType(String key) {
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
        if (normalized.contains("密码") || normalized.contains("口令") || normalized.contains("密钥")
                || normalized.contains("secret") || normalized.contains("2fa") || normalized.contains("token")) {
            return "PASSWORD";
        }
        if (normalized.contains("邮箱") || normalized.contains("email") || normalized.contains("mail")) {
            return "EMAIL";
        }
        if (normalized.contains("网址") || normalized.contains("url") || normalized.contains("http")) {
            return "URL";
        }
        if (normalized.contains("手机") || normalized.contains("电话") || normalized.contains("phone")) {
            return "PHONE";
        }
        return "TEXT";
    }

    /**
     * 模型常把 JSON 包在 ```json ... ``` 中，或夹杂说明文字。
     */
    static String extractJsonObject(String raw) {
        if (raw == null) {
            return "{}";
        }
        String text = raw.trim();
        if (text.startsWith("```")) {
            int firstNl = text.indexOf('\n');
            if (firstNl > 0) {
                text = text.substring(firstNl + 1);
            }
            int fence = text.lastIndexOf("```");
            if (fence >= 0) {
                text = text.substring(0, fence);
            }
            text = text.trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private JsonNode toPayload(JsonNode item) {
        ObjectNode payload = emptyPayload(
                blankTo(text(item, "name"), "未命名记录"),
                text(item, "platform")
        );
        payload.put("channel", text(item, "channel"));
        payload.put("channelUrl", text(item, "channelUrl"));
        payload.put("notes", text(item, "notes"));
        String expiresAt = text(item, "expiresAt");
        if (!expiresAt.isBlank()) {
            payload.put("expiresAt", expiresAt);
        }
        ArrayNode fields = (ArrayNode) payload.get("fields");
        setField(fields, "account", "账号", "TEXT", text(item, "account"), false);
        setField(fields, "password", "密码", "PASSWORD", text(item, "password"), true);
        JsonNode extras = item.get("extraFields");
        if (extras != null && extras.isArray()) {
            for (JsonNode extra : extras) {
                ObjectNode field = fields.addObject();
                field.put("id", UUID.randomUUID().toString());
                field.put("name", blankTo(text(extra, "name"), "字段"));
                field.put("type", normalizeType(text(extra, "type")));
                field.put("value", text(extra, "value"));
                field.put("required", false);
                field.put("sensitive", "PASSWORD".equals(normalizeType(text(extra, "type"))));
                field.put("copyable", true);
                field.put("hint", "");
                field.put("order", fields.size() - 1);
            }
        }
        return payload;
    }

    private ObjectNode emptyPayload(String name, String platform) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("name", name);
        payload.put("platform", platform);
        payload.put("channel", "");
        payload.put("channelUrl", "");
        payload.put("status", "NORMAL");
        payload.putNull("expiresAt");
        payload.putArray("tags");
        payload.put("pinned", false);
        payload.putArray("fields");
        payload.putNull("templateSnapshot");
        payload.put("notes", "");
        return payload;
    }

    private void setField(ArrayNode fields, String systemKey, String name, String type, String value, boolean sensitive) {
        ObjectNode field = fields.addObject();
        field.put("id", UUID.randomUUID().toString());
        field.put("name", name);
        field.put("type", type);
        field.put("value", value);
        field.put("required", false);
        field.put("sensitive", sensitive);
        field.put("copyable", true);
        field.put("hint", "");
        field.put("order", fields.size() - 1);
        field.put("systemKey", systemKey);
    }

    private static String first(Map<String, String> cells, String... keys) {
        for (String key : keys) {
            for (Map.Entry<String, String> entry : cells.entrySet()) {
                if (entry.getKey() != null && entry.getKey().trim().equalsIgnoreCase(key)) {
                    String value = entry.getValue();
                    if (value != null && !value.isBlank()) {
                        return value.trim();
                    }
                }
            }
        }
        return null;
    }

    private static boolean isReserved(String key) {
        String normalized = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        return List.of("名称", "标题", "平台", "渠道", "渠道网址", "网址", "账号", "用户名", "邮箱", "密码", "口令", "备注", "状态", "有效期", "原文",
                "name", "title", "platform", "channel", "url", "website", "account", "username", "email", "password", "notes")
                .contains(normalized);
    }

    private static String normalizeType(String type) {
        String upper = type == null ? "TEXT" : type.trim().toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "PASSWORD", "EMAIL", "URL", "PHONE" -> upper;
            default -> "TEXT";
        };
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.asText("").trim();
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static double clamp(double value) {
        if (value < 0) {
            return 0;
        }
        if (value > 1) {
            return 1;
        }
        return value;
    }
}
