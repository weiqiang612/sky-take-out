package com.weiqiang.skyai.exception;

import lombok.Getter;

/**
 * 接口滑动窗口超限自定义异常
 */
@Getter
public class RateLimitExceededException extends RuntimeException {
    
    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super("请求过于频繁，请稍后再试");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
