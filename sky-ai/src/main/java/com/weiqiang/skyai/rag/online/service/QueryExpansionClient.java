package com.weiqiang.skyai.rag.online.service;

public interface QueryExpansionClient {

    String generate(String query, int maxQueries);
}
