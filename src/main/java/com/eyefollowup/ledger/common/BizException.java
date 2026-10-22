package com.eyefollowup.ledger.common;

import lombok.Getter;

/**
 * 业务异常：由全局异常处理器转换为 {code, message} 响应。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(400, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
