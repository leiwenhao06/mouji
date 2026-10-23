package com.eyefollowup.ledger.config;

import com.eyefollowup.ledger.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

/**
 * 鉴权拦截器：校验 Bearer Token，并把用户名/角色写入 request attribute。
 * 放行：登录、健康检查、静态资源、SPA 页面。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String path = request.getRequestURI();

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (!path.startsWith("/api/")) {
            return true;
        }
        if (path.equals("/api/auth/login") || path.equals("/api/health")) {
            return true;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return reject(response, 401, "未登录或登录已过期");
        }

        try {
            Claims claims = jwtUtil.parse(header.substring(7));
            String username = claims.getSubject();
            String role = String.valueOf(claims.get("role"));
            request.setAttribute("username", username);
            request.setAttribute("role", role);

            // 用户管理与审计查询仅管理员可用
            if ((path.startsWith("/api/users") || path.startsWith("/api/audit")) && !"admin".equals(role)) {
                return reject(response, 403, "仅管理员可访问");
            }
            return true;
        } catch (Exception e) {
            return reject(response, 401, "登录已过期，请重新登录");
        }
    }

    private boolean reject(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(code == 403 ? 403 : 401);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(code, message)));
        return false;
    }
}
