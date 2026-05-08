package com.weiqiang.skyai.rag.online.service;

import com.weiqiang.skyai.rag.online.config.OnlineRetrievalProperties;
import com.weiqiang.skyai.rag.online.model.RetrievalResult;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.springframework.http.MediaType;

class OnlineRetrievalServiceIntegrationTests {

    @Test
    void retrieveRunsFullPipeline() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(OnlineRetrievalIntegrationTestConfiguration.class)) {
            OnlineRetrievalProperties properties = context.getBean(OnlineRetrievalProperties.class);
            properties.setTopK(4);
            properties.setTopN(2);
            properties.getSiliconFlow().setApiKey("test-key");
            properties.getSiliconFlow().setBaseUrl("https://api.siliconflow.cn");

            MutableVectorStoreStub vectorStoreStub = context.getBean(MutableVectorStoreStub.class);
            vectorStoreStub.setSearchResults(List.of(
                    Document.builder().text("chunk-a").metadata(Map.of("source", "doc-a")).score(0.81d).build(),
                    Document.builder().text("chunk-b").metadata(Map.of("source", "doc-b")).score(0.73d).build()
            ));

            RestClient.Builder restClientBuilder = context.getBean(RestClient.Builder.class);
            MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
            server.expect(requestTo("https://api.siliconflow.cn/v1/rerank"))
                    .andExpect(method(POST))
                    .andExpect(header("Authorization", "Bearer test-key"))
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(content().string("""
                            {"model":"BAAI/bge-reranker-v2-m3","query":"what is chunk-b","documents":["chunk-a","chunk-b"],"top_n":2,"return_documents":true}
                            """.trim()))
                    .andRespond(withSuccess("""
                            {
                              "id": "rerank-123",
                              "results": [
                                {
                                  "index": 1,
                                  "document": {
                                    "text": "chunk-a"
                                  },
                                  "relevance_score": 0.82
                                },
                                {
                                  "index": 2,
                                  "document": {
                                    "text": "chunk-b"
                                  },
                                  "relevance_score": 0.98
                                }
                              ],
                              "meta": {
                                "tokens": {
                                  "input_tokens": 10,
                                  "output_tokens": 0,
                                  "image_tokens": 0
                                }
                              }
                            }
                            """, MediaType.APPLICATION_JSON));

            OnlineRetrievalService onlineRetrievalService = context.getBean(OnlineRetrievalService.class);
            RetrievalResult result = onlineRetrievalService.retrieve("what is chunk-b");

            assertEquals(2, result.chunks().size());
            assertEquals("chunk-b", result.chunks().get(0).content());
            assertTrue(result.context().contains("chunk-a"));
            assertTrue(result.context().contains("chunk-b"));
            server.verify();
        }
    }
}
