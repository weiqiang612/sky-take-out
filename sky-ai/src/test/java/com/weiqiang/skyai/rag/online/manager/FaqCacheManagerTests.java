package com.weiqiang.skyai.rag.online.manager;

import com.weiqiang.skyai.rag.online.config.FaqCacheProperties;
import com.weiqiang.skyai.rag.online.model.FaqCacheItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FaqCacheManagerTests {

    private JdbcTemplate jdbcTemplate;
    private FaqCacheProperties properties;
    private RedissonClient redissonClient;
    private RTopic topic;
    private FaqCacheManager manager;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        properties = mock(FaqCacheProperties.class);
        redissonClient = mock(RedissonClient.class);
        topic = mock(RTopic.class);

        when(properties.isEnabled()).thenReturn(true);
        when(properties.getSimilarityThreshold()).thenReturn(0.95);
        when(redissonClient.getTopic(anyString())).thenReturn(topic);
        when(topic.addListener(eq(String.class), any(MessageListener.class))).thenReturn(1);

        manager = new FaqCacheManager(jdbcTemplate, properties, redissonClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void initAndReloadLoadsFaqItemsAndParsesData() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper<FaqCacheItem> mapper = invocation.getArgument(1);
            // 模拟 ResultSet 映射得到缓存对象
            java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
            when(rs.getString("chunk_id")).thenReturn("chunk-123");
            when(rs.getString("content")).thenReturn("Question: 发票怎么开？\nAnswer: 请在个人中心中选择订单点击开发票。");
            when(rs.getString("embedding")).thenReturn("[0.1, 0.2, 0.3, 0.4]");

            FaqCacheItem mapped = mapper.mapRow(rs, 0);
            return Collections.singletonList(mapped);
        });

        manager.init();

        verify(jdbcTemplate, times(1)).query(anyString(), any(RowMapper.class));
        verify(redissonClient).getTopic("channel:faq_sync");
        verify(topic).addListener(eq(String.class), any(MessageListener.class));

        // 验证余弦相似度匹配 - 完全相同向量
        float[] queryVector = new float[]{0.1f, 0.2f, 0.3f, 0.4f};
        FaqCacheItem matched = manager.match(queryVector);
        assertNotNull(matched);
        assertEquals("chunk-123", matched.getId());
        assertEquals("发票怎么开？", matched.getQuestion());
        assertEquals("请在个人中心中选择订单点击开发票。", matched.getAnswer());

        // 验证不相似向量不能匹配
        float[] nonMatchVector = new float[]{-0.1f, -0.2f, -0.3f, -0.4f};
        FaqCacheItem unmatched = manager.match(nonMatchVector);
        assertNull(unmatched);

        manager.destroy();
        verify(topic).removeListener(eq(1));
    }

    @Test
    @SuppressWarnings("unchecked")
    void syncTopicSignalTriggersReload() {
        ArgumentCaptor<MessageListener> listenerCaptor = ArgumentCaptor.forClass(MessageListener.class);
        manager.init();

        verify(topic).addListener(eq(String.class), listenerCaptor.capture());
        MessageListener<String> listener = listenerCaptor.getValue();
        assertNotNull(listener);

        // 手动模拟接收到同步信号
        listener.onMessage("channel:faq_sync", "reload-signal");

        // 异步重新加载会执行加载逻辑。由于我们使用的是自建的单线程异步池，为了让其执行完，休眠 200ms
        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {}

        // query 应该被调用了至少 2 次（1次是 init，1次是 sync 信号）
        verify(jdbcTemplate, times(2)).query(anyString(), any(RowMapper.class));

        manager.destroy();
    }
}
