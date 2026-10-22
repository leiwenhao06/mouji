package com.eyefollowup.ledger.controller;

import com.eyefollowup.ledger.common.ApiResponse;
import com.eyefollowup.ledger.dto.ExamRequest;
import com.eyefollowup.ledger.entity.FollowupExam;
import com.eyefollowup.ledger.service.ExamService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/exams")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;

    @PostMapping
    public ApiResponse<FollowupExam> create(@Valid @RequestBody ExamRequest request) {
        return ApiResponse.ok(examService.create(request));
    }

    @GetMapping("/{examId}")
    public ApiResponse<FollowupExam> get(@PathVariable int examId) {
        return ApiResponse.ok(examService.get(examId));
    }

    @DeleteMapping("/{examId}")
    public ApiResponse<Void> delete(@PathVariable int examId) {
        examService.delete(examId);
        return ApiResponse.ok();
    }
}
