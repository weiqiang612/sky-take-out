package com.weiqiang.skyai.rag.offline.store;

public interface RagChunkSearchProjection {

    String getContent();

    String getMetadataJson();

    Double getScore();
}
