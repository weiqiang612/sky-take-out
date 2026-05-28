package com.weiqiang.skyai.rag.online.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiqiang.skyai.rag.online.config.OnlineRetrievalProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

@TestConfiguration
@Import({
        QueryExpansionService.class,
        EmbeddingRetrievalService.class,
        KeywordRetrievalService.class,
        RetrievalFusionService.class,
        CandidateRetrievalService.class,
        RerankingService.class,
        ContextAssemblyService.class,
        OnlineRetrievalService.class
})
class OnlineRetrievalUnitTestConfiguration {

    @Bean
    OnlineRetrievalProperties onlineRetrievalProperties() {
        return new OnlineRetrievalProperties();
    }

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    MutableVectorStoreStub vectorStore() {
        return new MutableVectorStoreStub();
    }

    @Bean
    MutableRagIndexRepositoryStub ragIndexRepository() {
        return new MutableRagIndexRepositoryStub();
    }

    @Bean
    MutableRerankerClientStub rerankerClient() {
        return new MutableRerankerClientStub();
    }
}
