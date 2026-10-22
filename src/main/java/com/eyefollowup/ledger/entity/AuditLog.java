package com.eyefollowup.ledger.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作审计日志（仅追加）。
 */
@Data
public class AuditLog {
    private Integer logId;
    private String username;
    private String module;
    private String action;
    private String detail;
    private String patientId;
    private String ip;
    private LocalDateTime createdAt;
}
