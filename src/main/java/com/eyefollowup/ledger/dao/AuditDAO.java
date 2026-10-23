package com.eyefollowup.ledger.dao;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审计日志：只追加，不提供修改/删除。
 */
@Repository
@RequiredArgsConstructor
public class AuditDAO {

    private final JdbcTemplate jdbc;

    public void insert(String username, String module, String action, String detail, String patientId, String ip) {
        jdbc.update("INSERT INTO audit_logs (username, module, action, detail, patient_id, ip) VALUES (?,?,?,?,?,?)",
                username == null ? "anonymous" : username, module, action,
                detail == null ? null : (detail.length() > 500 ? detail.substring(0, 500) : detail),
                patientId, ip);
    }

    /** 条件片段与参数一一对应，全部走占位符绑定，杜绝拼接注入。 */
    private static String where(String module, String username) {
        StringBuilder sb = new StringBuilder(" WHERE 1=1");
        if (module != null && !module.isBlank()) {
            sb.append(" AND module = ?");
        }
        if (username != null && !username.isBlank()) {
            sb.append(" AND username LIKE ?");
        }
        return sb.toString();
    }

    private static Object[] filterArgs(String module, String username) {
        List<Object> args = new java.util.ArrayList<>(2);
        if (module != null && !module.isBlank()) {
            args.add(module);
        }
        if (username != null && !username.isBlank()) {
            args.add("%" + username + "%");
        }
        return args.toArray();
    }

    public List<Map<String, Object>> list(String module, String username, int offset, int size) {
        Object[] filter = filterArgs(module, username);
        Object[] params = java.util.Arrays.copyOf(filter, filter.length + 2);
        params[filter.length] = size;
        params[filter.length + 1] = offset;
        return jdbc.query(
                "SELECT log_id, username, module, action, detail, patient_id, ip, created_at FROM audit_logs"
                        + where(module, username) + " ORDER BY log_id DESC LIMIT ? OFFSET ?",
                (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("log_id"));
                    m.put("username", rs.getString("username"));
                    m.put("module", rs.getString("module"));
                    m.put("action", rs.getString("action"));
                    m.put("detail", rs.getString("detail"));
                    m.put("patientId", rs.getString("patient_id"));
                    m.put("ip", rs.getString("ip"));
                    Timestamp t = rs.getTimestamp("created_at");
                    m.put("createdAt", t == null ? null : t.toLocalDateTime().toString());
                    return m;
                },
                params);
    }

    public long count(String module, String username) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs" + where(module, username),
                Long.class, filterArgs(module, username));
        return n == null ? 0 : n;
    }

    public List<String> distinctModules() {
        return jdbc.queryForList("SELECT DISTINCT module FROM audit_logs ORDER BY module", String.class);
    }
}
