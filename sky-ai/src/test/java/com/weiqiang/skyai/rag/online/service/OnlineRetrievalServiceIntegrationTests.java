package com.weiqiang.skyai.rag.online.service;

import com.weiqiang.skyai.rag.online.config.OnlineRetrievalProperties;
import com.weiqiang.skyai.rag.online.model.RetrievalResult;
import com.weiqiang.skyai.rag.offline.model.KeywordSearchResult;
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
                    Document.builder().text("chunk-a").metadata(Map.of("documentId", "doc-a", "source", "doc-a")).score(0.81d).build(),
                    Document.builder().text("chunk-b").metadata(Map.of("documentId", "doc-b", "source", "doc-b")).score(0.73d).build()
            ));

            MutableRagIndexRepositoryStub ragIndexRepository = context.getBean(MutableRagIndexRepositoryStub.class);
            ragIndexRepository.setActiveDocumentIds(List.of("doc-a", "doc-b"));

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

    @Test
    void retrieveFusesOriginalExpandedAndKeywordCandidatesBeforeSingleRerank() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(OnlineRetrievalIntegrationTestConfiguration.class)) {
            OnlineRetrievalProperties properties = context.getBean(OnlineRetrievalProperties.class);
            properties.setTopK(3);
            properties.setTopN(3);
            properties.getQueryExpansion().setMaxQueries(1);
            properties.getKeyword().setTopK(2);
            properties.getSiliconFlow().setApiKey("test-key");
            properties.getSiliconFlow().setBaseUrl("https://api.siliconflow.cn");

            MutableQueryExpansionClientStub queryExpansionClient = context.getBean(MutableQueryExpansionClientStub.class);
            queryExpansionClient.setResponse("[\"callback 支付回调\"]");

            MutableVectorStoreStub vectorStoreStub = context.getBean(MutableVectorStoreStub.class);
            vectorStoreStub.setSearchResults("callback", List.of(
                    Document.builder().text("vector original").metadata(Map.of("chunkHash", "hash-vector", "documentId", "doc-vector")).score(0.91d).build()
            ));
            vectorStoreStub.setSearchResults("callback 支付回调", List.of(
                    Document.builder().text("vector expanded").metadata(Map.of("chunkHash", "hash-expanded", "documentId", "doc-expanded")).score(0.87d).build()
            ));

            MutableRagIndexRepositoryStub ragIndexRepository = context.getBean(MutableRagIndexRepositoryStub.class);
            ragIndexRepository.setActiveDocumentIds(List.of("doc-vector", "doc-expanded", "doc-keyword"));

            ragIndexRepository.setKeywordResults(List.of(
                    new KeywordSearchResult("keyword exact", Map.of("chunkHash", "hash-keyword", "documentId", "doc-keyword"), 4.0d)
            ));

            RestClient.Builder restClientBuilder = context.getBean(RestClient.Builder.class);
            MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
            server.expect(requestTo("https://api.siliconflow.cn/v1/rerank"))
                    .andExpect(method(POST))
                    .andExpect(header("Authorization", "Bearer test-key"))
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(content().string("""
                            {"model":"BAAI/bge-reranker-v2-m3","query":"callback","documents":["vector original","keyword exact","vector expanded"],"top_n":3,"return_documents":true}
                            """.trim()))
                    .andRespond(withSuccess("""
                            {
                              "results": [
                                {
                                  "index": 2,
                                  "document": {
                                    "text": "vector expanded"
                                  },
                                  "relevance_score": 0.99
                                },
                                {
                                  "index": 1,
                                  "document": {
                                    "text": "keyword exact"
                                  },
                                  "relevance_score": 0.88
                                },
                                {
                                  "index": 0,
                                  "document": {
                                    "text": "vector original"
                                  },
                                  "relevance_score": 0.77
                                }
                              ]
                            }
                            """, MediaType.APPLICATION_JSON));

            OnlineRetrievalService onlineRetrievalService = context.getBean(OnlineRetrievalService.class);
            RetrievalResult result = onlineRetrievalService.retrieve("callback");

            assertEquals(2, vectorStoreStub.getSearchRequests().size());
            assertEquals("vector expanded", result.chunks().get(0).content());
            assertEquals("keyword exact", result.chunks().get(1).content());
            assertEquals(List.of("keyword"), result.chunks().get(1).metadata().get("retrievalSources"));
            server.verify();
        }
    }
}
