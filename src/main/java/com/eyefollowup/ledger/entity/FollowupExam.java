package com.eyefollowup.ledger.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 随访检查记录：一次检查对应一只眼。
 */
@Data
public class FollowupExam {
    private Integer examId;
    private String patientId;
    private LocalDate examDate;
    private LocalTime examTime;
    private String eyeSide;
    private String examType;
    private String notes;
    private String riskFlag;
    private String stageCode;
    private LocalDateTime createdAt;

    /** 非数据库字段：查询时联表带出的地形图与生物力学参数。 */
    private CornealTopography topography;
    private BiomechanicalParams biomechanics;
    private String imagePath;
}
