package com.weiqiang.skyai.rag.online.service;

import com.weiqiang.skyai.rag.online.config.OnlineRetrievalProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(KeywordRetrievalService.class)
class KeywordRetrievalServiceTestConfiguration {

    @Bean
    OnlineRetrievalProperties onlineRetrievalProperties() {
        return new OnlineRetrievalProperties();
    }

    @Bean
    MutableKeywordChunkRepositoryStub keywordChunkRepository() {
        return new MutableKeywordChunkRepositoryStub();
    }
}
