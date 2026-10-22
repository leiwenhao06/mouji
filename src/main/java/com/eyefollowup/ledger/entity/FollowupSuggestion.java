package com.eyefollowup.ledger.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 随访建议任务/结果。
 */
@Data
public class FollowupSuggestion {
    private Integer suggestionId;
    private String requestId;
    private String patientId;
    private String inputMode;
    private String status;
    private String engine;
    private String summary;
    private String resultJson;
    private String disclaimer;
    private String errorMessage;
    private String requestedBy;
    private LocalDateTime requestedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long waitDurationMs;
    private Long durationMs;
    private Boolean adopted;
    private String adoptedBy;
    private LocalDateTime adoptedAt;

    /** 非数据库字段。 */
    private String patientName;
}
