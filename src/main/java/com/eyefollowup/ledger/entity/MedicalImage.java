package com.eyefollowup.ledger.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 医学影像元数据（文件保存于本地 data/images）。
 */
@Data
public class MedicalImage {
    private Integer imageId;
    private String patientId;
    private Integer examId;
    private String imageType;
    private String eyeSide;
    private String imagePath;
    private String fileName;
    private Long fileSize;
    private String ocrJson;
    private String reportType;
    private BigDecimal ocrConfidence;
    private LocalDateTime uploadDate;
}
