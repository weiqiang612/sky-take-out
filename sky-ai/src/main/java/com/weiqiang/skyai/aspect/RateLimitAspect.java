package com.weiqiang.skyai.aspect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiqiang.skyai.annotation.RateLimit;
import com.weiqiang.skyai.config.RateLimitManager;
import com.weiqiang.skyai.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimitManager rateLimitManager;
    private final ObjectMapper objectMapper;

    @Around("@annotation(rateLimit)")
    public Object intercept(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();
        String userId = resolveUserId(request, joinPoint);

        if (!rateLimitManager.tryAcquire(userId)) {
            long retryAfter = rateLimitManager.getRetryAfterSeconds(userId);
            log.warn("Rate limit exceeded for user: {}. Retry after {} seconds.", userId, retryAfter);
            throw new RateLimitExceededException(retryAfter);
        }

        return joinPoint.proceed();
    }

    /**
     * 多级智能提取并确定用户 ID 标识链：
     * 1. 优先读取 Header 中的 'authentication' (即 JWT Token) 并免签解密其 Payload 提取 'userId'。
     * 2. 作为第二备选，尝试从 Request Parameters 中捕获 'userId'。
     * 3. 作为第三备选，尝试反射匹配被调用方法的形参，寻找匹配 'userId' 名字的实际参数值。
     * 4. 最底层的终极退避：使用 'anonymous' 作为通用匿名标记。
     */
    private String resolveUserId(HttpServletRequest request, ProceedingJoinPoint joinPoint) {
        // 1. JWT 提取与解析
        String token = request.getHeader("authentication");
        if (StringUtils.hasText(token)) {
            if (token.startsWith("Bearer ")) {
                token = token.substring(7).trim();
            }
            String userIdFromJwt = extractUserIdFromJwt(token);
            if (StringUtils.hasText(userIdFromJwt)) {
                return userIdFromJwt;
            }
        }

        // 2. HTTP Request Parameter 备选
        String userIdParam = request.getParameter("userId");
        if (StringUtils.hasText(userIdParam)) {
            return userIdParam.trim();
        }

        // 3. 反射获取方法实参中名为 'userId' 的对象
        try {
            Object[] args = joinPoint.getArgs();
            String[] parameterNames = ((org.aspectj.lang.reflect.MethodSignature) joinPoint.getSignature()).getParameterNames();
            if (parameterNames != null) {
                for (int i = 0; i < parameterNames.length; i++) {
                    if ("userId".equals(parameterNames[i]) && args[i] != null) {
                        return String.valueOf(args[i]).trim();
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse userId from method parameters via AOP reflection", e);
        }

        // 4. 退避至匿名用户
        return "anonymous";
    }

    /**
     * 利用 Base64Url 强力秒级解码 JWT 第二段 Payload 提取其中的 userId (无需额外依赖)
     */
    private String extractUserIdFromJwt(String token) {
        if (!token.contains(".")) {
            return null;
        }
        try {
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                byte[] decodedBytes = Base64.getUrlDecoder().decode(parts[1]);
                String payloadJson = new String(decodedBytes, StandardCharsets.UTF_8);
                JsonNode rootNode = objectMapper.readTree(payloadJson);
                if (rootNode.has("userId")) {
                    return rootNode.get("userId").asText();
                }
            }
        } catch (Exception e) {
            log.error("Failed to decode and extract userId from JWT token during rate-limiting", e);
        }
        return null;
    }
}
