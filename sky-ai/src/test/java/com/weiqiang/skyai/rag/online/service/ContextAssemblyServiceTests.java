package com.weiqiang.skyai.rag.online.service;

import com.weiqiang.skyai.rag.online.model.RetrievedChunk;
import com.weiqiang.skyai.rag.online.model.RetrievalResult;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextAssemblyServiceTests {

    @Test
    void assembleUsesPreferredMetadataTextWhenPresent() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(OnlineRetrievalUnitTestConfiguration.class)) {
            ContextAssemblyService contextAssemblyService = context.getBean(ContextAssemblyService.class);

            RetrievedChunk chunk1 = new RetrievedChunk("raw-1", Map.of("expandedText", "expanded-1"), 0.9d, 0.98d);
            RetrievedChunk chunk2 = new RetrievedChunk("raw-2", Map.of("source", "doc-2"), 0.8d, 0.88d);

            RetrievalResult result = contextAssemblyService.assemble("query", List.of(chunk1, chunk2));

            assertTrue(result.context().contains("expanded-1"));
            assertTrue(result.context().contains("raw-2"));
        }
    }
}
