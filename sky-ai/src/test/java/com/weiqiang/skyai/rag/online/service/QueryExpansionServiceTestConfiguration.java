package com.weiqiang.skyai.rag.online.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiqiang.skyai.rag.online.config.OnlineRetrievalProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(QueryExpansionService.class)
class QueryExpansionServiceTestConfiguration {

    @Bean
    OnlineRetrievalProperties onlineRetrievalProperties() {
        return new OnlineRetrievalProperties();
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    MutableQueryExpansionClientStub queryExpansionClient() {
        return new MutableQueryExpansionClientStub();
    }
}
