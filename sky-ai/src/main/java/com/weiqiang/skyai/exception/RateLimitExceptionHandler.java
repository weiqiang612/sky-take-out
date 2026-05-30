package com.weiqiang.skyai.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * REST 限流超速全局控制器增强处理器
 */
@RestControllerAdvice
public class RateLimitExceptionHandler {

    /**
     * 专门捕获接口受限流拦截后抛出的 RateLimitExceededException 自定义异常
     *
     * @param ex 异常对象
     * @return 符合接口规范的 HTTP 429 响应及 Retry-After 响应头
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimitExceededException(RateLimitExceededException ex) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("code", 429);
        body.put("message", "请求过于频繁，请稍后再试");
        body.put("data", null);

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(body);
    }
}
