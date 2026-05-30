package com.weiqiang.skyai.rag.online.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * FAQ 语义缓存配置参数
 *
 * @author antigravity
 * @date 2026/05/30
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "skyai.faq-cache")
public class FaqCacheProperties {

    private boolean enabled = true;
    private double similarityThreshold = 0.95;
}
