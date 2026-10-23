package com.eyefollowup.ledger.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 登录失败限流规则测试（时间源可注入，不依赖真实时钟）。
 */
class LoginAttemptLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(now::get);

    @Test
    void lockedAfterFiveFailures() {
        for (int i = 1; i <= 4; i++) {
            limiter.onFailure("admin");
            assertFalse(limiter.isLocked("admin"));
        }
        limiter.onFailure("admin");
        assertTrue(limiter.isLocked("admin"));
        assertEquals(0, limiter.remaining("admin"));
    }

    @Test
    void successResetsCounter() {
        for (int i = 0; i < 4; i++) {
            limiter.onFailure("admin");
        }
        limiter.onSuccess("admin");
        assertEquals(LoginAttemptLimiter.MAX_FAILURES, limiter.remaining("admin"));
        assertFalse(limiter.isLocked("admin"));
    }

    @Test
    void lockExpiresAfterWindow() {
        for (int i = 0; i < 5; i++) {
            limiter.onFailure("admin");
        }
        assertTrue(limiter.isLocked("admin"));
        now.addAndGet(LoginAttemptLimiter.WINDOW_MS + 1);
        assertFalse(limiter.isLocked("admin"));
        assertEquals(LoginAttemptLimiter.MAX_FAILURES, limiter.remaining("admin"));
    }

    @Test
    void usersAreIsolated() {
        for (int i = 0; i < 5; i++) {
            limiter.onFailure("admin");
        }
        assertTrue(limiter.isLocked("admin"));
        assertFalse(limiter.isLocked("doctor"));
        assertFalse(limiter.isLocked(" Admin "), "大小写不同的用户名不共享计数");
    }

    @Test
    void attemptsOutsideWindowDoNotAccumulate() {
        for (int i = 0; i < 4; i++) {
            limiter.onFailure("admin");
            now.addAndGet(LoginAttemptLimiter.WINDOW_MS / 2);
        }
        // 每次间隔 5 分钟，都不在同一窗口内，不应锁定
        assertFalse(limiter.isLocked("admin"));
    }
}
