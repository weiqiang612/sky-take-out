package com.weiqiang.skyai.rag.offline.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@TestConfiguration
@Import(TsQueryFormatter.class)
class TsQueryFormatterTestConfiguration {

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    MutableKeywordExtractionClientStub keywordExtractionClient() {
        return new MutableKeywordExtractionClientStub();
    }
}
