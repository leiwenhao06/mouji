package com.eyefollowup.ledger.service;

import com.eyefollowup.ledger.common.BizException;
import com.eyefollowup.ledger.dao.UserDAO;
import com.eyefollowup.ledger.dto.UserCreateRequest;
import com.eyefollowup.ledger.entity.User;
import lombok.RequiredArgsConstructor;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户管理（仅管理员）。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserDAO userDAO;
    private final AuditService auditService;

    public List<User> list() {
        List<User> users = userDAO.list();
        users.forEach(u -> u.setPasswordHash(null));
        return users;
    }

    public void create(UserCreateRequest req) {
        if (userDAO.findByUsername(req.getUsername().trim()) != null) {
            throw new BizException("用户名已存在");
        }
        String role = "admin".equals(req.getRole()) ? "admin" : "user";
        userDAO.insert(req.getUsername().trim(),
                BCrypt.hashpw(req.getPassword(), BCrypt.gensalt()),
                req.getDisplayName() == null || req.getDisplayName().isBlank()
                        ? req.getUsername().trim() : req.getDisplayName().trim(),
                role);
        auditService.log("USER", "CREATE", "新建用户 " + req.getUsername(), null);
    }

    public void resetPassword(int id, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new BizException("密码至少 6 位");
        }
        userDAO.updatePassword(id, BCrypt.hashpw(newPassword, BCrypt.gensalt()));
        auditService.log("USER", "RESET_PASSWORD", "重置用户 #" + id + " 密码", null);
    }
}
