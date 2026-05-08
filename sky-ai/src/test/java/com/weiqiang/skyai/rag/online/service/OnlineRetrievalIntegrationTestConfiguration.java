package com.weiqiang.skyai.rag.online.service;

import com.weiqiang.skyai.rag.online.config.OnlineRetrievalProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

@Configuration
@Import({
        EmbeddingRetrievalService.class,
        RerankingService.class,
        ContextAssemblyService.class,
        OnlineRetrievalService.class,
        OllamaEmbedRerankerClient.class
})
class OnlineRetrievalIntegrationTestConfiguration {

    @Bean
    OnlineRetrievalProperties onlineRetrievalProperties() {
        return new OnlineRetrievalProperties();
    }

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    MutableVectorStoreStub vectorStore() {
        return new MutableVectorStoreStub();
    }
}
