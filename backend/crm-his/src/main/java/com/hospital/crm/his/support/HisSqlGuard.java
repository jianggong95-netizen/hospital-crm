package com.hospital.crm.his.support;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HIS SQL 只读守卫：
 * 1. 只允许单条 SELECT，拒绝分号、注释、写操作与危险关键字；
 * 2. FROM / JOIN 引用的视图必须位于配置白名单；
 * 3. 所有表名与字段名必须为合法标识符，禁止拼接任意 SQL。
 */
public final class HisSqlGuard {

    private static final Pattern IDENTIFIER =
            Pattern.compile("^[A-Za-z_][A-Za-z0-9_$]*(\\.[A-Za-z_][A-Za-z0-9_$]*){0,2}$");

    private static final Pattern FORBIDDEN_KEYWORDS = Pattern.compile(
            "(?i)\\b(insert|update|delete|merge|exec|execute|drop|alter|create|truncate|grant|revoke|"
                    + "backup|restore|into|openrowset|opendatasource|openquery|bulk|waitfor|shutdown|use|"
                    + "dbcc|union|xp_cmdshell|sp_executesql|sp_)\\b");

    private static final Pattern TABLE_REFERENCE = Pattern.compile(
            "(?i)\\b(?:from|join)\\s+((?:\\[[^\\]]+\\]|[A-Za-z_][A-Za-z0-9_$]*)"
                    + "(?:\\s*\\.\\s*(?:\\[[^\\]]+\\]|[A-Za-z_][A-Za-z0-9_$]*)){0,2})");

    private HisSqlGuard() {
    }

    public static void validateSelect(String sql, Set<String> allowedTables) {
        if (sql == null || sql.isBlank()) {
            throw invalid("HIS 查询语句不能为空");
        }
        String trimmed = sql.trim();
        if (!trimmed.regionMatches(true, 0, "select", 0, "select".length())) {
            throw invalid("HIS 适配器只允许 SELECT 查询");
        }
        if (trimmed.contains(";") || trimmed.contains("--") || trimmed.contains("/*") || trimmed.contains("*/")) {
            throw invalid("HIS 查询语句不允许分号或注释");
        }
        if (FORBIDDEN_KEYWORDS.matcher(trimmed).find()) {
            throw invalid("HIS 查询语句包含不允许的关键字");
        }

        Set<String> normalizedAllowed = allowedTables == null
                ? Set.of()
                : allowedTables.stream()
                .map(HisSqlGuard::normalizeTable)
                .collect(java.util.stream.Collectors.toSet());
        if (normalizedAllowed.isEmpty()) {
            throw invalid("HIS 查询视图未配置，拒绝访问");
        }

        Matcher matcher = TABLE_REFERENCE.matcher(trimmed);
        int tableCount = 0;
        while (matcher.find()) {
            tableCount++;
            String table = normalizeTable(matcher.group(1));
            if (!normalizedAllowed.contains(table)) {
                throw invalid("HIS 查询视图不在白名单内：" + table);
            }
        }
        if (tableCount == 0) {
            throw invalid("HIS 查询必须指定白名单视图");
        }
    }

    public static String requireIdentifier(String identifier, String label) {
        if (identifier == null || identifier.isBlank()) {
            throw invalid("HIS " + label + "未配置");
        }
        String trimmed = identifier.trim();
        if (!IDENTIFIER.matcher(trimmed).matches()) {
            throw invalid("HIS " + label + "不是合法标识符：" + trimmed);
        }
        return trimmed;
    }

    private static String normalizeTable(String table) {
        return table.replace("[", "")
                .replace("]", "")
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, message);
    }
}
