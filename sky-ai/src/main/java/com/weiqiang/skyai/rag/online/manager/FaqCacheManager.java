package com.weiqiang.skyai.rag.online.manager;

import com.weiqiang.skyai.rag.online.config.FaqCacheProperties;
import com.weiqiang.skyai.rag.online.model.FaqCacheItem;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * FAQ 语义缓存管理器，负责在 JVM 内存中维护 FAQ 向量并实现相似度比对，接收 Redisson 发布订阅同步更新。
 *
 * @author antigravity
 * @date 2026/05/30
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FaqCacheManager {

    private static final String SYNC_TOPIC = "channel:faq_sync";
    
    private static final String LOAD_FAQ_SQL = """
            SELECT vc.embedding, c.content, c.chunk_id
            FROM rag_chunk c
            JOIN rag_document d ON c.document_id = d.document_id
            JOIN vector_store vc ON c.vector_store_id = CAST(vc.id AS VARCHAR)
            WHERE d.active = true AND d.document_type = 'QA'
            """;

    private final JdbcTemplate jdbcTemplate;
    private final FaqCacheProperties properties;
    private final RedissonClient redissonClient;

    private final List<FaqCacheItem> cacheItems = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "faq-cache-ttl-scheduler");
        thread.setDaemon(true);
        return thread;
    });

    private int listenerId = -1;

    @PostConstruct
    public void init() {
        log.info("Initializing FaqCacheManager...");
        // 1. 同步加载 FAQ 向量数据
        reload();

        // 2. 注册 Redisson 广播监听，实现多微服务节点缓存同步
        try {
            RTopic topic = redissonClient.getTopic(SYNC_TOPIC);
            this.listenerId = topic.addListener(String.class, (channel, msg) -> {
                log.info("Received FAQ sync signal via Redisson on channel [{}]: {}", channel, msg);
                reloadAsync();
            });
            log.info("Successfully subscribed to Redisson topic: {}", SYNC_TOPIC);
        } catch (Exception ex) {
            log.error("Failed to subscribe to Redisson topic [{}]", SYNC_TOPIC, ex);
        }

        // 3. 启动 7 天本地兜底刷新定时器
        scheduler.scheduleWithFixedDelay(this::reloadAsync, 7, 7, TimeUnit.DAYS);
    }

    @PreDestroy
    public void destroy() {
        try {
            scheduler.shutdown();
            RTopic topic = redissonClient.getTopic(SYNC_TOPIC);
            if (this.listenerId >= 0) {
                topic.removeListener(this.listenerId);
            }
            log.info("Unsubscribed from Redisson topic and shut down scheduler.");
        } catch (Exception ex) {
            log.warn("Error during pre-destroy cleanup", ex);
        }
    }

    /**
     * 重新加载 FAQ 缓存（同步）
     */
    public synchronized void reload() {
        if (!properties.isEnabled()) {
            log.info("FAQ Cache is disabled by configuration. Clearing cache...");
            cacheItems.clear();
            return;
        }

        log.info("Loading FAQ vectors from PostgreSQL...");
        try {
            List<FaqCacheItem> loaded = jdbcTemplate.query(LOAD_FAQ_SQL, (rs, rowNum) -> {
                String chunkId = rs.getString("chunk_id");
                String content = rs.getString("content");
                String embeddingStr = rs.getString("embedding");

                String question = "";
                String answer = "";
                if (content != null) {
                    int qIdx = content.indexOf("Question:");
                    int aIdx = content.indexOf("\nAnswer:");
                    if (qIdx != -1 && aIdx != -1 && aIdx > qIdx) {
                        question = content.substring(qIdx + "Question:".length(), aIdx).trim();
                        answer = content.substring(aIdx + "\nAnswer:".length()).trim();
                    } else {
                        question = content;
                        answer = content;
                    }
                }

                float[] vector = parseVector(embeddingStr);
                return new FaqCacheItem(chunkId, question, answer, vector);
            });

            // 过滤向量无效的 FAQ 项
            List<FaqCacheItem> validItems = new ArrayList<>();
            for (FaqCacheItem item : loaded) {
                if (item.getVector() != null && item.getVector().length > 0) {
                    validItems.add(item);
                }
            }

            cacheItems.clear();
            cacheItems.addAll(validItems);
            log.info("FAQ Cache loaded successfully. Total items: {}", cacheItems.size());
        } catch (Exception ex) {
            log.error("Failed to load FAQ vectors from database", ex);
        }
    }

    /**
     * 异步重新加载 FAQ 缓存，防止阻塞消息监听或定时线程
     */
    private void reloadAsync() {
        scheduler.submit(this::reload);
    }

    /**
     * 执行余弦相似度匹配，找到相似度最大且超过阈值的 FAQ
     *
     * @param queryVector 用户当前提问的向量
     * @return 命中的 FAQ 项；若未命中则返回 null
     */
    public FaqCacheItem match(float[] queryVector) {
        if (!properties.isEnabled() || cacheItems.isEmpty() || queryVector == null) {
            return null;
        }

        double threshold = properties.getSimilarityThreshold();
        FaqCacheItem bestMatch = null;
        float maxSimilarity = -1.0f;

        for (FaqCacheItem item : cacheItems) {
            float similarity = cosineSimilarity(queryVector, item.getVector());
            if (similarity > threshold && similarity > maxSimilarity) {
                maxSimilarity = similarity;
                bestMatch = item;
            }
        }

        if (bestMatch != null) {
            log.info("FAQ cache HIT! matched question='{}', score={}", bestMatch.getQuestion(), maxSimilarity);
        }
        return bestMatch;
    }

    /**
     * 解析 PG vector 返回的字符串为 float[]
     */
    private float[] parseVector(String embeddingStr) {
        if (embeddingStr == null || !embeddingStr.startsWith("[") || !embeddingStr.endsWith("]")) {
            return null;
        }
        try {
            String[] parts = embeddingStr.substring(1, embeddingStr.length() - 1).split(",");
            float[] vector = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                vector[i] = Float.parseFloat(parts[i].trim());
            }
            return vector;
        } catch (Exception ex) {
            log.warn("Failed to parse vector string: {}", embeddingStr, ex);
            return null;
        }
    }

    /**
     * 计算两个向量的余弦相似度
     */
    private float cosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA == null || vectorB == null || vectorA.length != vectorB.length) {
            return 0.0f;
        }
        float dotProduct = 0.0f;
        float normA = 0.0f;
        float normB = 0.0f;
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }
        if (normA == 0.0f || normB == 0.0f) {
            return 0.0f;
        }
        return (float) (dotProduct / (Math.sqrt(normA) * Math.sqrt(normB)));
    }


}
