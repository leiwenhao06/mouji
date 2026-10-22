package com.eyefollowup.ledger.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.time.LocalDate;

@Data
public class PatientRequest {
    @NotBlank(message = "姓名不能为空")
    private String name;
    private String gender;
    private LocalDate dateOfBirth;
    private String contact;
    private String medicalHistory;
}
