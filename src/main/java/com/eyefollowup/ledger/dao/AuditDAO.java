package com.eyefollowup.ledger.dao;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
}
