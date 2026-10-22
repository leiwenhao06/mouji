package com.eyefollowup.ledger.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 前端构建产物由后端托管时的 SPA 路由回退。
 * 直接访问 /patients/xxx 等前端路由时转发到 index.html。
 */
@Controller
public class SpaController {

    @GetMapping({"/", "/login", "/patients", "/patients/**", "/import",
            "/followup", "/suggestions", "/dashboard", "/users"})
    public String index() {
        return "forward:/index.html";
    }
}
