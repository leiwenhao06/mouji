package com.eyefollowup.ledger.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.time.LocalDate;

@Data
public class PlanCreateRequest {
    @NotBlank(message = "患者ID不能为空")
    private String patientId;
    private Integer frequencyMonths = 6;
    private LocalDate nextDueDate;
    private String basis;
}
