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

    private final SiliconFlow siliconFlow = new SiliconFlow();

    private final Context context = new Context();

    private final QueryExpansion queryExpansion = new QueryExpansion();

    private final Keyword keyword = new Keyword();

    private final Fusion fusion = new Fusion();

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

    public SiliconFlow getSiliconFlow() {
        return siliconFlow;
    }

    public Context getContext() {
        return context;
    }

    public QueryExpansion getQueryExpansion() {
        return queryExpansion;
    }

    public Keyword getKeyword() {
        return keyword;
    }

    public Fusion getFusion() {
        return fusion;
    }

    public static class SiliconFlow {

        private String baseUrl = "https://api.siliconflow.cn";

        private String apiKey;

        private String model = "BAAI/bge-reranker-v2-m3";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
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

    public static class QueryExpansion {

        private boolean enabled = true;

        private int maxQueries = 2;

        private boolean fallbackOnFailure = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxQueries() {
            return maxQueries;
        }

        public void setMaxQueries(int maxQueries) {
            this.maxQueries = maxQueries;
        }

        public boolean isFallbackOnFailure() {
            return fallbackOnFailure;
        }

        public void setFallbackOnFailure(boolean fallbackOnFailure) {
            this.fallbackOnFailure = fallbackOnFailure;
        }
    }

    public static class Keyword {

        private boolean enabled = true;

        private int topK = 40;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }
    }

    public static class Fusion {

        private int maxCandidates = 120;

        private int rrfK = 60;

        public int getMaxCandidates() {
            return maxCandidates;
        }

        public void setMaxCandidates(int maxCandidates) {
            this.maxCandidates = maxCandidates;
        }

        public int getRrfK() {
            return rrfK;
        }

        public void setRrfK(int rrfK) {
            this.rrfK = rrfK;
        }
    }
}
