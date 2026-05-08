package com.weiqiang.skyai.rag.offline.chunk;

import com.weiqiang.skyai.rag.offline.model.DocumentType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ChunkingStrategyRegistry {

    private final Map<DocumentType, ChunkingStrategy> strategies = new EnumMap<>(DocumentType.class);

    public ChunkingStrategyRegistry(List<ChunkingStrategy> strategies) {
        for (ChunkingStrategy strategy : strategies) {
            this.strategies.put(strategy.supports(), strategy);
        }
    }

    public ChunkingStrategy get(DocumentType documentType) {
        ChunkingStrategy strategy = strategies.get(documentType);
        if (strategy == null) {
            throw new IllegalArgumentException("No chunking strategy for document type: " + documentType);
        }
        return strategy;
    }
}
