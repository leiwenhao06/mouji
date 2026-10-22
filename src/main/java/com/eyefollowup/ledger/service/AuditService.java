package com.eyefollowup.ledger.service;

import com.eyefollowup.ledger.dao.AuditDAO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

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
}
