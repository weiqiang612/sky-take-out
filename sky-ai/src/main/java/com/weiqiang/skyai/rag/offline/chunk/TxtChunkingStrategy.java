package com.weiqiang.skyai.rag.offline.chunk;

import com.weiqiang.skyai.rag.offline.model.DocumentType;
import com.weiqiang.skyai.rag.offline.model.ParsedDocument;
import com.weiqiang.skyai.rag.offline.model.RagChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class TxtChunkingStrategy extends AbstractChunkingStrategy {

    @Override
    public DocumentType supports() {
        return DocumentType.TXT;
    }

    @Override
    public List<RagChunk> chunk(ParsedDocument document) {
        List<String> parts = splitByParagraphThenLength(document.content(), DEFAULT_MAX_CHARS, DEFAULT_OVERLAP_CHARS);
        List<RagChunk> chunks = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            Map<String, Object> metadata = baseMetadata(document);
            chunks.add(RagChunk.of(document, i, parts.get(i), metadata));
        }
        return chunks;
    }
}
