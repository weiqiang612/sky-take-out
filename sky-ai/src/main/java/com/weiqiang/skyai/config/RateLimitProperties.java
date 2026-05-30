package com.weiqiang.skyai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "skyai.rate-limit")
public class RateLimitProperties {
    /**
     * 滑动窗口内允许的最大请求次数
     */
    private int requestsPerMinute = 20;

    /**
     * 限流滑动窗口的时间长度（秒）
     */
    private int windowSeconds = 60;
}
