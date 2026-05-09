package com.weiqiang.skyai.rag.offline.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
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
