package com.eyefollowup.ledger.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 随访计划：台账的核心，记录"下次什么时候复查、为什么"。
 */
@Data
public class FollowupPlan {
    private Integer planId;
    private String patientId;
    private String stageCode;
    private Integer frequencyMonths;
    private LocalDate lastExamDate;
    private LocalDate nextDueDate;
    private String status;
    private String basis;
    private Integer suggestionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 非数据库字段：列表页联表带出的患者姓名。 */
    private String patientName;
    private String riskFlag;
}
