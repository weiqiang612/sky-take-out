package com.weiqiang.skyai.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Logs outbound OpenAI chat-completions tool count for debugging provider tool limits.
 */
@Component
@ConditionalOnProperty(value = "skyai.debug.log-openai-tools", havingValue = "true", matchIfMissing = true)
public class OpenAiToolCountLoggingCustomizer implements RestClientCustomizer {

    private static final Logger log = LoggerFactory.getLogger(OpenAiToolCountLoggingCustomizer.class);

    private final ObjectMapper objectMapper;

    public OpenAiToolCountLoggingCustomizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void customize(RestClient.Builder restClientBuilder) {
        restClientBuilder.requestInterceptor((request, body, execution) -> {
            try {
                String path = request.getURI().getPath();
                if (path != null && path.contains("/chat/completions") && body != null && body.length > 0) {
                    JsonNode root = objectMapper.readTree(new String(body, StandardCharsets.UTF_8));
                    JsonNode tools = root.path("tools");
                    int toolCount = tools.isArray() ? tools.size() : 0;
                    log.info("OpenAI outbound tools count={}, model={}, toolNames={}",
                            toolCount,
                            root.path("model").asText("unknown"),
                            previewToolNames(tools));
                }
            } catch (Exception ex) {
                log.debug("Failed to inspect outbound OpenAI request body", ex);
            }
            return execution.execute(request, body);
        });
    }

    private String previewToolNames(JsonNode tools) {
        if (!tools.isArray() || tools.isEmpty()) {
            return "[]";
        }
        List<String> names = new ArrayList<>();
        for (JsonNode tool : tools) {
            String name = tool.path("function").path("name").asText(null);
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
            if (names.size() >= 30) {
                break;
            }
        }
        String suffix = tools.size() > 30 ? " ..." : "";
        return names.toString() + suffix;
    }
}

