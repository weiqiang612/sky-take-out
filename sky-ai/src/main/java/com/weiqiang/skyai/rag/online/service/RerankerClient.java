package com.weiqiang.skyai.rag.online.service;

import com.weiqiang.skyai.rag.online.model.RetrievedChunk;

import java.util.List;

/**
 * 重排服务接口，定义了重排器的基本功能
 *
 * @author weiqiang
 * @date 2024/6/17
 */
public interface RerankerClient {

    List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates);
}
