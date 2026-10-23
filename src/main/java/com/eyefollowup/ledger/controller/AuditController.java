package com.eyefollowup.ledger.controller;

import com.eyefollowup.ledger.common.ApiResponse;
import com.eyefollowup.ledger.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 审计日志查询（仅管理员，权限在 AuthInterceptor 统一校验）。
 */
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    public ApiResponse<Map<String, Object>> query(@RequestParam(required = false) String module,
                                                  @RequestParam(required = false) String username,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(auditService.query(module, username, page, size));
    }
}
