package com.eyefollowup.ledger.service;

import com.eyefollowup.ledger.common.BizException;
import com.eyefollowup.ledger.config.JwtUtil;
import com.eyefollowup.ledger.dao.UserDAO;
import com.eyefollowup.ledger.dto.LoginRequest;
import com.eyefollowup.ledger.dto.LoginResponse;
import com.eyefollowup.ledger.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

/**
 * 登录与初始管理员种子。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserDAO userDAO;
    private final JwtUtil jwtUtil;

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password:Admin@2026}")
    private String adminPassword;

    @PostConstruct
    public void seedAdmin() {
        if (userDAO.count() == 0) {
            userDAO.insert(adminUsername, BCrypt.hashpw(adminPassword, BCrypt.gensalt()), "系统管理员", "admin");
            log.info("已初始化管理员账号: {}（密码来自配置，请在生产环境修改）", adminUsername);
        }
    }

    public LoginResponse login(LoginRequest req) {
        User user = userDAO.findByUsername(req.getUsername().trim());
        if (user == null || !BCrypt.checkpw(req.getPassword(), user.getPasswordHash())) {
            throw new BizException(401, "用户名或密码错误");
        }
        String token = jwtUtil.generate(user.getUsername(), user.getRole());
        return new LoginResponse(token, user.getUsername(),
                user.getDisplayName() == null ? user.getUsername() : user.getDisplayName(), user.getRole());
    }
}
