package com.eyefollowup.ledger.dao;

import com.eyefollowup.ledger.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

/**
 * 用户数据访问。
 */
@Repository
@RequiredArgsConstructor
public class UserDAO {

    private final JdbcTemplate jdbc;

    private static final RowMapper<User> MAPPER = (rs, i) -> {
        User u = new User();
        u.setId(rs.getInt("id"));
        u.setUsername(rs.getString("username"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setDisplayName(rs.getString("display_name"));
        u.setAvatarPath(rs.getString("avatar_path"));
        u.setRole(rs.getString("role"));
        u.setToken(rs.getString("token"));
        Timestamp t = rs.getTimestamp("created_at");
        u.setCreatedAt(t == null ? null : t.toLocalDateTime());
        return u;
    };

    public User findByUsername(String username) {
        List<User> list = jdbc.query("SELECT * FROM users WHERE username=?", MAPPER, username);
        return list.isEmpty() ? null : list.get(0);
    }

    public long count() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class);
        return n == null ? 0 : n;
    }

    public void insert(String username, String passwordHash, String displayName, String role) {
        jdbc.update("INSERT INTO users (username, password_hash, display_name, role) VALUES (?, ?, ?, ?)",
                username, passwordHash, displayName, role);
    }

    public List<User> list() {
        return jdbc.query("SELECT * FROM users ORDER BY id", MAPPER);
    }

    public void updatePassword(int id, String passwordHash) {
        jdbc.update("UPDATE users SET password_hash=? WHERE id=?", passwordHash, id);
    }

    public void updateDisplayName(String username, String displayName) {
        jdbc.update("UPDATE users SET display_name=? WHERE username=?", displayName, username);
    }

    public void updateAvatar(String username, String avatarPath) {
        jdbc.update("UPDATE users SET avatar_path=? WHERE username=?", avatarPath, username);
    }
}
