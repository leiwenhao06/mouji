package com.eyefollowup.ledger.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 登录失败限流：同一用户名 10 分钟内连续失败 5 次即锁定，成功登录后清零。
 * 内存实现，重启即失效；仅用于演示环境的暴力破解防护叙事。
 */
@Component
public class LoginAttemptLimiter {

    static final int MAX_FAILURES = 5;
    static final long WINDOW_MS = 10 * 60 * 1000L;
    private static final int MAX_TRACKED = 10_000;

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public LoginAttemptLimiter() {
        this(System::currentTimeMillis);
    }

    LoginAttemptLimiter(LongSupplier clock) {
        this.clock = clock;
    }

    public boolean isLocked(String username) {
        String key = key(username);
        Attempt a = attempts.get(key);
        if (a == null) {
            return false;
        }
        if (expired(a, clock.getAsLong())) {
            attempts.remove(key);
            return false;
        }
        return a.count() >= MAX_FAILURES;
    }

    /** 剩余可尝试次数，供提示文案使用。 */
    public int remaining(String username) {
        Attempt a = attempts.get(key(username));
        if (a == null || expired(a, clock.getAsLong())) {
            return MAX_FAILURES;
        }
        return Math.max(0, MAX_FAILURES - a.count());
    }

    public void onFailure(String username) {
        long now = clock.getAsLong();
        if (attempts.size() > MAX_TRACKED) {
            attempts.entrySet().removeIf(e -> expired(e.getValue(), now));
        }
        attempts.compute(key(username), (k, a) -> {
            if (a == null || expired(a, now)) {
                return new Attempt(1, now);
            }
            return new Attempt(a.count() + 1, a.firstAt());
        });
    }

    public void onSuccess(String username) {
        attempts.remove(key(username));
    }

    private boolean expired(Attempt a, long now) {
        return now - a.firstAt() > WINDOW_MS;
    }

    private String key(String username) {
        return username == null ? "" : username.trim();
    }

    record Attempt(int count, long firstAt) {
    }
}
