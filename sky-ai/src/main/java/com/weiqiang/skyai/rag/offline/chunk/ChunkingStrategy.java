package com.weiqiang.skyai.rag.offline.chunk;

import com.weiqiang.skyai.rag.offline.model.DocumentType;
import com.weiqiang.skyai.rag.offline.model.ParsedDocument;
import com.weiqiang.skyai.rag.offline.model.RagChunk;

import java.util.List;

public interface ChunkingStrategy {

    DocumentType supports();

    List<RagChunk> chunk(ParsedDocument document);
}
