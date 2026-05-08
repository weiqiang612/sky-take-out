package com.weiqiang.skyai.rag.offline.chunk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiqiang.skyai.rag.offline.model.DocumentType;
import com.weiqiang.skyai.rag.offline.model.ParsedDocument;
import com.weiqiang.skyai.rag.offline.model.RagChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class QaChunkingStrategy extends AbstractChunkingStrategy {

    private final ObjectMapper objectMapper;

    @Override
    public DocumentType supports() {
        return DocumentType.QA;
    }

    @Override
    public List<RagChunk> chunk(ParsedDocument document) {
        List<QaPair> pairs = parsePairs(document.content());
        List<RagChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;
        for (QaPair pair : pairs) {
            String questionPrefix = "Question: " + pair.question() + "\nAnswer: ";
            int answerMaxChars = Math.max(300, DEFAULT_MAX_CHARS - questionPrefix.length());
            List<String> answerParts = splitLongText(pair.answer(), answerMaxChars, DEFAULT_OVERLAP_CHARS);
            if (answerParts.isEmpty()) {
                answerParts = List.of("");
            }
            for (String answerPart : answerParts) {
                Map<String, Object> metadata = baseMetadata(document);
                metadata.put("category", pair.category());
                metadata.put("relatedId", pair.relatedId());
                chunks.add(RagChunk.of(document, chunkIndex++, questionPrefix + answerPart, metadata));
            }
        }
        return chunks;
    }

    private List<QaPair> parsePairs(String content) {
        List<QaPair> pairs = new ArrayList<>();
        String normalized = content == null ? "" : content.replace("\r\n", "\n");
        for (String line : normalized.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            QaPair pair = parseJsonLine(line);
            if (pair != null) {
                pairs.add(pair);
            }
        }
        if (!pairs.isEmpty()) {
            return pairs;
        }
        return parsePlainQa(normalized);
    }

    private QaPair parseJsonLine(String line) {
        try {
            JsonNode node = objectMapper.readTree(line);
            String question = text(node, "question", "q");
            String answer = text(node, "answer", "a");
            if (question == null || answer == null) {
                return null;
            }
            return new QaPair(question, answer, text(node, "category"), text(node, "relatedId", "id"));
        } catch (Exception ex) {
            return null;
        }
    }

    private List<QaPair> parsePlainQa(String content) {
        List<QaPair> pairs = new ArrayList<>();
        String question = null;
        StringBuilder answer = new StringBuilder();
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("Q:") || trimmed.startsWith("Question:") || trimmed.startsWith("问题:")) {
                if (question != null) {
                    pairs.add(new QaPair(question, answer.toString().trim(), null, null));
                    answer.setLength(0);
                }
                question = trimmed.substring(trimmed.indexOf(':') + 1).trim();
            } else if (trimmed.startsWith("A:") || trimmed.startsWith("Answer:") || trimmed.startsWith("答案:")) {
                answer.append(trimmed.substring(trimmed.indexOf(':') + 1).trim()).append('\n');
            } else if (question != null) {
                answer.append(line).append('\n');
            }
        }
        if (question != null) {
            pairs.add(new QaPair(question, answer.toString().trim(), null, null));
        }
        return pairs;
    }

    private String text(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                return value.asText();
            }
        }
        return null;
    }

    private record QaPair(String question, String answer, String category, String relatedId) {
    }
}
