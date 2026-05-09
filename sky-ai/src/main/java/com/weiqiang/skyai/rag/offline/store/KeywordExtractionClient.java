package com.weiqiang.skyai.rag.offline.store;

import java.util.List;

public interface KeywordExtractionClient {

    List<String> extract(String query, int maxKeywords);
}
