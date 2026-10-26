package com.eyefollowup.ledger.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

/**
 * JWT 签发与校验工具（HS256）。
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expire-hours:8}")
    private int expireHours;

    private Key key;

    @PostConstruct
    public void init() {
        // HS256 要求密钥至少 32 字节
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret 长度必须不少于 32 字节");
        }
        String envSecret = System.getenv("JWT_SECRET");
        if (envSecret == null || envSecret.isBlank()) {
            log.warn("未设置 JWT_SECRET，正在使用默认演示密钥；生产环境必须通过环境变量覆盖（至少 32 字符）");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public String generate(String username, String role) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expireHours * 3600_000L);
        return Jwts.builder()
                .setSubject(username)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
