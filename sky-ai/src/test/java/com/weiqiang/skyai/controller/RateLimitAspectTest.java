package com.weiqiang.skyai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiqiang.skyai.annotation.RateLimit;
import com.weiqiang.skyai.aspect.RateLimitAspect;
import com.weiqiang.skyai.config.RateLimitManager;
import com.weiqiang.skyai.exception.RateLimitExceededException;
import com.weiqiang.skyai.exception.RateLimitExceptionHandler;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitAspectTest {

    private RateLimitManager rateLimitManager;
    private RateLimitAspect aspect;

    @BeforeEach
    void setUp() {
        rateLimitManager = mock(RateLimitManager.class);
        ObjectMapper objectMapper = new ObjectMapper();
        aspect = new RateLimitAspect(rateLimitManager, objectMapper);
    }

    @Test
    void shouldExceptionHandlerWorkCorrectly() {
        // 100% 纯 Java 单元化验证全局限流异常捕获器映射的正确性
        RateLimitExceptionHandler handler = new RateLimitExceptionHandler();
        RateLimitExceededException ex = new RateLimitExceededException(45L);

        ResponseEntity<Map<String, Object>> response = handler.handleRateLimitExceededException(ex);

        // 验证返回状态码是否为 429
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        // 验证是否携带了正确的限流冷却时间 Retry-After 头
        assertEquals("45", response.getHeaders().getFirst("Retry-After"));
        // 验证返回响应体 JSON 契约
        Map<String, Object> body = response.getBody();
        assert body != null;
        assertEquals(429, body.get("code"));
        assertEquals("请求过于频繁，请稍后再试", body.get("message"));
    }

    @Test
    void shouldAspectThrowExceptionOnRateLimitExceeded() throws Throwable {
        // 单元化验证 AOP 切面的拦截限流逻辑
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        RateLimit rateLimit = mock(RateLimit.class);
        HttpServletRequest request = mock(HttpServletRequest.class);

        // 显式 Mock 运行时 RequestContext 上下文环境
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes);

        try {
            // 输入包含 {"userId":"user123"} 明文 payload 的 Base64Url 编码模拟 JWT
            when(request.getHeader("authentication")).thenReturn("Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOiJ1c2VyMTIzIn0.signature");
            when(rateLimitManager.tryAcquire("user123")).thenReturn(false);
            when(rateLimitManager.getRetryAfterSeconds("user123")).thenReturn(30L);

            // 验证切面是否会在超限时抛出期望的 RateLimitExceededException 异常
            assertThrows(RateLimitExceededException.class, () -> aspect.intercept(joinPoint, rateLimit));
        } finally {
            // 清理上下文资源
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void shouldAspectProceedWhenWithinLimit() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        RateLimit rateLimit = mock(RateLimit.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        MethodSignature signature = mock(MethodSignature.class);

        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes);

        try {
            when(request.getHeader("authentication")).thenReturn("Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOiJ1c2VyMTIzIn0.signature");
            when(rateLimitManager.tryAcquire("user123")).thenReturn(true);
            when(joinPoint.getSignature()).thenReturn(signature);

            aspect.intercept(joinPoint, rateLimit);

            // 验证是否顺利放行并执行了实际方法 (proceed)
            verify(joinPoint).proceed();
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
