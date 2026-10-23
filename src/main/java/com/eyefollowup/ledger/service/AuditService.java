package com.eyefollowup.ledger.service;

import com.eyefollowup.ledger.dao.AuditDAO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审计服务：自动从当前请求取用户名与来源 IP；异步线程可直接指定用户名。
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditDAO auditDAO;

    public void log(String module, String action, String detail, String patientId) {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        String username = "system";
        String ip = null;
        if (attrs instanceof ServletRequestAttributes sra) {
            HttpServletRequest req = sra.getRequest();
            Object u = req.getAttribute("username");
            username = u == null ? "anonymous" : u.toString();
            ip = req.getRemoteAddr();
        }
        auditDAO.insert(username, module, action, detail, patientId, ip);
    }

    public void logAs(String username, String module, String action, String detail, String patientId, String ip) {
        auditDAO.insert(username, module, action, detail, patientId, ip);
    }

    /** 审计日志分页查询（仅管理员页面使用）。 */
    public Map<String, Object> query(String module, String username, int page, int size) {
        int p = Math.max(1, page);
        int s = Math.min(100, Math.max(1, size));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", auditDAO.list(module, username, (p - 1) * s, s));
        result.put("total", auditDAO.count(module, username));
        result.put("modules", auditDAO.distinctModules());
        return result;
    }
}
