package com.weiqiang.skyai.rag.online.service;

import com.weiqiang.skyai.rag.online.config.OnlineRetrievalProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QueryExpansionServiceTests {

    @Test
    void expandParsesJsonArrayAndRemovesOriginalQuery() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(QueryExpansionServiceTestConfiguration.class)) {
            OnlineRetrievalProperties properties = context.getBean(OnlineRetrievalProperties.class);
            properties.getQueryExpansion().setMaxQueries(2);

            MutableQueryExpansionClientStub client = context.getBean(MutableQueryExpansionClientStub.class);
            client.setResponse("""
                    ["支付超时 callback 如何处理", "订单支付状态回调", "支付超时 callback 如何处理"]
                    """);

            QueryExpansionService service = context.getBean(QueryExpansionService.class);
            List<String> queries = service.expand("支付超时 callback");

            assertEquals(List.of("支付超时 callback 如何处理", "订单支付状态回调"), queries);
        }
    }

    @Test
    void expandReturnsEmptyListWhenClientReturnsEmptyArray() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(QueryExpansionServiceTestConfiguration.class)) {
            MutableQueryExpansionClientStub client = context.getBean(MutableQueryExpansionClientStub.class);
            client.setResponse("[]");

            QueryExpansionService service = context.getBean(QueryExpansionService.class);

            assertEquals(List.of(), service.expand("退款"));
        }
    }

    @Test
    void expandFallsBackToEmptyListWhenClientFails() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(QueryExpansionServiceTestConfiguration.class)) {
            OnlineRetrievalProperties properties = context.getBean(OnlineRetrievalProperties.class);
            properties.getQueryExpansion().setFallbackOnFailure(true);

            MutableQueryExpansionClientStub client = context.getBean(MutableQueryExpansionClientStub.class);
            client.setFailure(new IllegalStateException("llm failed"));

            QueryExpansionService service = context.getBean(QueryExpansionService.class);

            assertEquals(List.of(), service.expand("优惠券"));
        }
    }
}
