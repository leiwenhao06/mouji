package com.eyefollowup.ledger.controller;

import com.eyefollowup.ledger.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", "ok");
        map.put("service", "eye-followup-ledger");
        map.put("time", LocalDateTime.now());
        return ApiResponse.ok(map);
    }
}
