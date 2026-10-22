package com.eyefollowup.ledger.controller;

import com.eyefollowup.ledger.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    @GetMapping
    public ApiResponse<Map<String, Object>> me(@RequestAttribute("username") String username,
                                               @RequestAttribute("role") String role) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("username", username);
        map.put("role", role);
        return ApiResponse.ok(map);
    }
}
