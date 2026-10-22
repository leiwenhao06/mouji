package com.eyefollowup.ledger.dto;

import com.eyefollowup.ledger.entity.BiomechanicalParams;
import com.eyefollowup.ledger.entity.CornealTopography;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 报告导入确认：前端把 OCR 预览数据（可人工修正）提交入库。
 */
@Data
public class ImportConfirmRequest {
    private Integer imageId;
    /** 已有患者时直接传 patientId；否则按姓名+出生日期匹配或新建。 */
    private String patientId;
    private String name;
    private String gender;
    private LocalDate dateOfBirth;

    private LocalDate examDate;
    private LocalTime examTime;
    private String eyeSide;
    private String examType;
    private String notes;

    private CornealTopography topography;
    private BiomechanicalParams biomechanics;
    private String ocrJson;
}
