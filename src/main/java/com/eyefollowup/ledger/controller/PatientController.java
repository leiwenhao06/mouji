package com.eyefollowup.ledger.controller;

import com.eyefollowup.ledger.common.ApiResponse;
import com.eyefollowup.ledger.dto.PatientRequest;
import com.eyefollowup.ledger.entity.FollowupExam;
import com.eyefollowup.ledger.entity.Patient;
import com.eyefollowup.ledger.service.ExamService;
import com.eyefollowup.ledger.service.PatientService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;
    private final ExamService examService;

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String gender,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(patientService.list(keyword, gender, page, size));
    }

    @GetMapping("/{patientId}")
    public ApiResponse<Patient> get(@PathVariable String patientId) {
        return ApiResponse.ok(patientService.get(patientId));
    }

    @PostMapping
    public ApiResponse<Patient> create(@Valid @RequestBody PatientRequest request) {
        return ApiResponse.ok(patientService.create(request));
    }

    @PutMapping("/{patientId}")
    public ApiResponse<Patient> update(@PathVariable String patientId,
                                       @Valid @RequestBody PatientRequest request) {
        return ApiResponse.ok(patientService.update(patientId, request));
    }

    @DeleteMapping("/{patientId}")
    public ApiResponse<Void> delete(@PathVariable String patientId) {
        patientService.delete(patientId);
        return ApiResponse.ok();
    }

    /** 台账时间线：患者信息 + 全部检查（含参数）+ 当前随访计划。 */
    @GetMapping("/{patientId}/timeline")
    public ApiResponse<Map<String, Object>> timeline(@PathVariable String patientId) {
        return ApiResponse.ok(examService.timeline(patientId));
    }

    @GetMapping("/{patientId}/exams")
    public ApiResponse<List<FollowupExam>> exams(@PathVariable String patientId) {
        return ApiResponse.ok(examService.listByPatient(patientId));
    }
}
