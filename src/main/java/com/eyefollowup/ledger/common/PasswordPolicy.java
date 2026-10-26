package com.eyefollowup.ledger.common;

/**
 * 统一的密码强度策略：8-64 位，至少包含一个字母与一个数字。
 */
public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null || password.length() < 8 || password.length() > 64) {
            throw new BizException("密码长度需为 8-64 位");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BizException("密码需同时包含字母和数字");
        }
    }
}
