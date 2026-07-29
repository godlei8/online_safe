package com.godlei.onlinesafe.vaultimport.application;

import com.godlei.onlinesafe.vaultimport.domain.ImportFileFormat;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MarkdownImportExtractor {

    private static final Pattern META = Pattern.compile("^-\\s*(平台|渠道|渠道网址|状态|有效期|备注)\\s*[：:]\\s*(.*)$");
    private static final Pattern TABLE_ROW = Pattern.compile("^\\|(.+)\\|$");
    private static final Pattern KV = Pattern.compile("^([^:：\\n|]{1,40})\\s*[：:]\\s*(.+)$");
    private static final Pattern HEADING = Pattern.compile("(?m)^##\\s+");
    private static final Pattern ACCOUNT_KEY = Pattern.compile("^(账号|用户名|邮箱|account|username|email)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PASSWORD_KEY = Pattern.compile("^(密码|口令|password)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL_ONLY = Pattern.compile("(?i)^(https?://\\S+|www\\.\\S+)$");

    public ImportDocument extract(String fileName, byte[] bytes) {
        String text = decode(bytes).replace("\r\n", "\n").replace('\r', '\n');
        List<String> blocks = splitBlocks(text);
        List<ImportDocument.RawRow> rows = new ArrayList<>();
        int index = 1;
        for (String block : blocks) {
            ImportDocument.RawRow row = parseBlock(index, block);
            if (row != null) {
                rows.add(row);
                index++;
            }
        }
        return new ImportDocument(fileName, ImportFileFormat.MD, bytes.length, List.copyOf(rows));
    }

    private ImportDocument.RawRow parseBlock(int index, String block) {
        String trimmed = block.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String[] lines = trimmed.split("\n");
        String name = null;
        Map<String, String> cells = new LinkedHashMap<>();
        boolean inFields = false;
        boolean headerSeen = false;
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.startsWith("## ")) {
                name = line.substring(3).trim();
                cells.put("名称", name);
                continue;
            }
            Matcher meta = META.matcher(line);
            if (meta.matches()) {
                cells.put(meta.group(1), meta.group(2).trim());
                continue;
            }
            if (line.startsWith("### ") && line.contains("字段")) {
                inFields = true;
                continue;
            }
            if (!inFields) {
                continue;
            }
            Matcher table = TABLE_ROW.matcher(line);
            if (!table.matches()) {
                continue;
            }
            String[] parts = table.group(1).split("\\|", -1);
            if (parts.length < 2) {
                continue;
            }
            String left = unescape(parts[0].trim());
            String right = unescape(parts[1].trim());
            if (!headerSeen) {
                headerSeen = true;
                continue;
            }
            if (left.matches("-+") || right.matches("-+")) {
                continue;
            }
            if (!left.isBlank() && !"—".equals(left)) {
                cells.put(left, "—".equals(right) ? "" : right);
            }
        }

        enrichFromKeyValueLines(cells, lines);

        if (name == null) {
            String inferred = inferName(lines, cells);
            if (inferred != null) {
                name = inferred;
                cells.putIfAbsent("名称", inferred);
            }
        }

        if (cells.isEmpty()) {
            cells.put("原文", trimmed);
            return new ImportDocument.RawRow(index, List.copyOf(cells.keySet()), cells, trimmed);
        }
        if (!cells.containsKey("名称")) {
            cells.putIfAbsent("名称", "未命名记录");
        }
        if (!cells.containsKey("备注") && !cells.containsKey("原文")) {
            String leftover = leftoverNotes(lines, cells);
            if (!leftover.isBlank()) {
                cells.put("备注", leftover);
            }
        }
        return new ImportDocument.RawRow(index, List.copyOf(cells.keySet()), cells, trimmed);
    }

    private static void enrichFromKeyValueLines(Map<String, String> cells, String[] lines) {
        for (String rawLine : lines) {
            String line = stripListMarker(rawLine.trim());
            if (line.isEmpty() || line.startsWith("#") || TABLE_ROW.matcher(line).matches()) {
                continue;
            }
            Matcher matcher = KV.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String key = matcher.group(1).trim();
            String value = matcher.group(2).trim();
            if (key.isEmpty() || value.isEmpty()) {
                continue;
            }
            cells.putIfAbsent(key, value);
        }
    }

    private static String inferName(String[] lines, Map<String, String> cells) {
        if (cells.containsKey("名称") && !isBlank(cells.get("名称"))) {
            return cells.get("名称").trim();
        }
        for (String rawLine : lines) {
            String line = stripListMarker(rawLine.trim());
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (KV.matcher(line).matches() || TABLE_ROW.matcher(line).matches()) {
                continue;
            }
            if (URL_ONLY.matcher(line).matches() || isNoiseLine(line)) {
                continue;
            }
            return line;
        }
        return null;
    }

    private static String leftoverNotes(String[] lines, Map<String, String> cells) {
        List<String> leftovers = new ArrayList<>();
        String name = cells.get("名称");
        for (String rawLine : lines) {
            String line = stripListMarker(rawLine.trim());
            if (line.isEmpty() || line.startsWith("#") || TABLE_ROW.matcher(line).matches()) {
                continue;
            }
            if (name != null && line.equals(name)) {
                continue;
            }
            Matcher matcher = KV.matcher(line);
            if (matcher.matches()) {
                String key = matcher.group(1).trim();
                if (cells.containsKey(key)) {
                    continue;
                }
            }
            leftovers.add(line);
        }
        return String.join("\n", leftovers);
    }

    private static String stripListMarker(String line) {
        if (line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ")) {
            return line.substring(2).trim();
        }
        return line;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static List<String> splitBlocks(String text) {
        if (HEADING.matcher(text).find()) {
            return splitByHeading(text);
        }
        return splitCredentialBlocks(text);
    }

    private static List<String> splitByHeading(String text) {
        String[] parts = text.split("(?m)(?=^## )");
        List<String> blocks = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                blocks.add(part.trim());
            }
        }
        if (blocks.isEmpty() && !text.isBlank()) {
            blocks.add(text.trim());
        }
        return blocks;
    }

    /**
     * 自由格式账密摘录：按「新记录起点」分块，而不是按空行。
     * 起点：Markdown 标题；或当前块已有账号/密码后出现新的账号行/记录标题。
     * 提示、链接等弱段落会并入上一条，避免拆成几十条碎片。
     */
    private static List<String> splitCredentialBlocks(String text) {
        String[] lines = text.split("\n", -1);
        List<String> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean hasAccount = false;
        boolean hasPassword = false;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            boolean blank = line.isEmpty();
            Matcher kv = blank ? null : KV.matcher(stripListMarker(line));
            boolean isKv = kv != null && kv.matches();
            String key = isKv ? kv.group(1).trim() : null;
            boolean isAccount = key != null && ACCOUNT_KEY.matcher(key).matches();
            boolean isPassword = key != null && PASSWORD_KEY.matcher(key).matches();
            boolean isMdHeading = !blank && line.matches("^#{1,3}\\s+.+");
            boolean isTitle = !blank && !isKv && !isMdHeading && !URL_ONLY.matcher(line).matches() && !isNoiseLine(line);

            boolean startsNew = false;
            if (!current.isEmpty() && !blank) {
                if (isMdHeading) {
                    startsNew = true;
                } else if (isAccount && hasAccount) {
                    startsNew = true;
                } else if (isTitle && looksLikeRecordTitle(line) && (hasAccount || hasPassword)) {
                    startsNew = true;
                }
            }

            if (startsNew) {
                String flushed = current.toString().trim();
                if (!flushed.isEmpty()) {
                    blocks.add(flushed);
                }
                current.setLength(0);
                hasAccount = false;
                hasPassword = false;
            }

            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(rawLine);
            if (isAccount) {
                hasAccount = true;
            }
            if (isPassword) {
                hasPassword = true;
            }
        }

        String last = current.toString().trim();
        if (!last.isEmpty()) {
            blocks.add(last);
        }
        if (blocks.isEmpty() && !text.isBlank()) {
            blocks.add(text.trim());
        }
        return mergeWeakTrailingBlocks(blocks);
    }

    /** 没有账号/密码的尾碎片并入上一条（温馨提示、教程链接等）。 */
    private static List<String> mergeWeakTrailingBlocks(List<String> blocks) {
        if (blocks.size() <= 1) {
            return blocks;
        }
        List<String> merged = new ArrayList<>();
        merged.add(blocks.getFirst());
        for (int i = 1; i < blocks.size(); i++) {
            String block = blocks.get(i);
            if (!hasCredentialKeys(block) && !looksLikeStandaloneRecord(block)) {
                int last = merged.size() - 1;
                merged.set(last, merged.get(last) + "\n\n" + block);
            } else {
                merged.add(block);
            }
        }
        return merged;
    }

    private static boolean hasCredentialKeys(String block) {
        for (String rawLine : block.split("\n")) {
            String line = stripListMarker(rawLine.trim());
            Matcher matcher = KV.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String key = matcher.group(1).trim();
            if (ACCOUNT_KEY.matcher(key).matches() || PASSWORD_KEY.matcher(key).matches()) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeStandaloneRecord(String block) {
        String[] lines = block.split("\n");
        int meaningful = 0;
        for (String rawLine : lines) {
            String line = stripListMarker(rawLine.trim());
            if (line.isEmpty() || isNoiseLine(line) || URL_ONLY.matcher(line).matches()) {
                continue;
            }
            meaningful++;
        }
        return meaningful >= 3;
    }

    private static boolean looksLikeRecordTitle(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        if (isNoiseLine(line)) {
            return false;
        }
        if (line.contains("账号") || line.contains("账户") || lower.contains("account")
                || lower.contains("chatgpt") || lower.contains("claude")
                || lower.contains("google") || line.contains("谷歌")
                || lower.contains("github") || lower.contains("openai")) {
            return true;
        }
        // 短标题行（非句子）
        return line.length() <= 40 && !line.contains("。") && !line.contains("！");
    }

    private static boolean isNoiseLine(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.startsWith("温馨提示")
                || lower.startsWith("提示")
                || lower.startsWith("注意")
                || lower.contains("教程")
                || lower.contains("如何使用")
                || lower.contains("docs.qq.com")
                || lower.contains("accountboy.com");
    }

    private static String unescape(String value) {
        return value.replace("\\|", "|").replace("<br>", "\n");
    }

    private static String decode(byte[] bytes) {
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (!utf8.contains("\uFFFD")) {
            return utf8;
        }
        return new String(bytes, Charset.forName("GBK"));
    }
}
