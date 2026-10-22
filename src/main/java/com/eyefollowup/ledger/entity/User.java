package com.eyefollowup.ledger.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户（密码存 BCrypt 哈希）。
 */
@Data
public class User {
    private Integer id;
    private String username;
    private String passwordHash;
    private String displayName;
    private String role;
    private String token;
    private LocalDateTime createdAt;
}
