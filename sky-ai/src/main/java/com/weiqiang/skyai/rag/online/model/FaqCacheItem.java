package com.weiqiang.skyai.rag.online.model;

import lombok.Getter;
import lombok.ToString;

/**
 * FAQ 内存缓存数据对象，封装向量及标准问答正文
 *
 * @author antigravity
 * @date 2026/05/30
 */
@Getter
@ToString
public class FaqCacheItem {

    private final String id;
    private final String question;
    private final String answer;
    private final float[] vector;

    public FaqCacheItem(String id, String question, String answer, float[] vector) {
        this.id = id;
        this.question = question;
        this.answer = answer;
        this.vector = vector;
    }
}
