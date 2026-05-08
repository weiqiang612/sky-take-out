package com.weiqiang.skyai.rag.online.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "skyai.retrieval.online")
public class OnlineRetrievalProperties {

    /**
     * Step 1: vector similarity search candidate size.
     */
    private int topK = 80;

    /**
     * Step 2: final reranked result count.
     */
    private int topN = 8;

    /**
     * Step 1 filter threshold.
     */
    private double similarityThreshold = 0.0d;

    /**
     * Step 2 fallback switch.
     */
    private boolean fallbackToEmbeddingResultsOnRerankFailure = true;

    private final Ollama ollama = new Ollama();

    private final Context context = new Context();

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public int getTopN() {
        return topN;
    }

    public void setTopN(int topN) {
        this.topN = topN;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public boolean isFallbackToEmbeddingResultsOnRerankFailure() {
        return fallbackToEmbeddingResultsOnRerankFailure;
    }

    public void setFallbackToEmbeddingResultsOnRerankFailure(boolean fallbackToEmbeddingResultsOnRerankFailure) {
        this.fallbackToEmbeddingResultsOnRerankFailure = fallbackToEmbeddingResultsOnRerankFailure;
    }

    public Ollama getOllama() {
        return ollama;
    }

    public Context getContext() {
        return context;
    }

    public static class Ollama {

        private String baseUrl = "http://127.0.0.1:11434";

        /**
         * Ollama embedding-style reranker model name.
         */
        private String rerankerModel = "bge-reranker-v2-m3:q4_k_m";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getRerankerModel() {
            return rerankerModel;
        }

        public void setRerankerModel(String rerankerModel) {
            this.rerankerModel = rerankerModel;
        }
    }

    public static class Context {

        private String chunkSeparator = "\n\n";

        private List<String> preferredTextMetadataKeys = List.of(
                "expandedText",
                "parentText",
                "parentDocument",
                "documentText"
        );

        public String getChunkSeparator() {
            return chunkSeparator;
        }

        public void setChunkSeparator(String chunkSeparator) {
            this.chunkSeparator = chunkSeparator;
        }

        public List<String> getPreferredTextMetadataKeys() {
            return preferredTextMetadataKeys;
        }

        public void setPreferredTextMetadataKeys(List<String> preferredTextMetadataKeys) {
            this.preferredTextMetadataKeys = preferredTextMetadataKeys;
        }
    }
}
