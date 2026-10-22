package com.eyefollowup.ledger.dto;

import com.eyefollowup.ledger.entity.BiomechanicalParams;
import com.eyefollowup.ledger.entity.CornealTopography;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class ExamRequest {
    @NotBlank(message = "患者ID不能为空")
    private String patientId;
    @NotNull(message = "检查日期不能为空")
    private LocalDate examDate;
    private LocalTime examTime;
    @NotBlank(message = "眼别不能为空")
    private String eyeSide;
    private String examType;
    private String notes;
    private CornealTopography topography;
    private BiomechanicalParams biomechanics;
}
