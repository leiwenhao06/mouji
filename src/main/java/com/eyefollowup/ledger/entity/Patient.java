package com.eyefollowup.ledger.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 患者基本信息（演示数据均为虚构）。
 */
@Data
public class Patient {
    private String patientId;
    private String name;
    private String gender;
    private LocalDate dateOfBirth;
    private Integer age;
    private String contact;
    private String medicalHistory;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
