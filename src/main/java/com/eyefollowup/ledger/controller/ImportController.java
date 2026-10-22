package com.eyefollowup.ledger.controller;

import com.eyefollowup.ledger.common.ApiResponse;
import com.eyefollowup.ledger.dto.ImportConfirmRequest;
import com.eyefollowup.ledger.gateway.OcrClient;
import com.eyefollowup.ledger.service.ImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;
    private final OcrClient ocrClient;

    /** 第一步：上传报告图片并返回 OCR 预览（不落库业务数据）。 */
    @PostMapping("/report")
    public ApiResponse<Map<String, Object>> preview(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(importService.preview(file));
    }

    /** 第二步：医生核对后确认入库（自动建档/建检查/进展检测/随访计划）。 */
    @PostMapping("/confirm")
    public ApiResponse<Map<String, Object>> confirm(@RequestBody ImportConfirmRequest request) {
        return ApiResponse.ok(importService.confirm(request));
    }

    @GetMapping("/ocr-health")
    public ApiResponse<Map<String, Object>> ocrHealth() {
        Map<String, Object> map = new LinkedHashMap<>();
        boolean ok = ocrClient.healthy();
        map.put("available", ok);
        map.put("message", ok ? "OCR 服务正常" : "OCR 服务不可用，可转手动录入");
        return ApiResponse.ok(map);
    }
}
