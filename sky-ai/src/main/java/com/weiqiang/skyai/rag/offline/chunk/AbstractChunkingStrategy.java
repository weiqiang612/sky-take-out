package com.weiqiang.skyai.rag.offline.chunk;

import com.weiqiang.skyai.rag.offline.model.ParsedDocument;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

abstract class AbstractChunkingStrategy implements ChunkingStrategy {

    protected static final int DEFAULT_MAX_CHARS = 1_200;
    protected static final int DEFAULT_OVERLAP_CHARS = 120;

    protected Map<String, Object> baseMetadata(ParsedDocument document) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("title", document.sourceName());
        metadata.put("sectionPath", List.of());
        metadata.put("category", null);
        metadata.put("relatedId", null);
        metadata.put("pageNumber", null);
        metadata.put("fileName", document.sourceName());
        metadata.put("tags", List.of());
        metadata.put("createdAt", OffsetDateTime.now().toString());
        return metadata;
    }

    protected List<String> splitByParagraphThenLength(String content, int maxChars, int overlapChars) {
        List<String> chunks = new ArrayList<>();
        String normalized = content == null ? "" : content.replace("\r\n", "\n").trim();
        if (normalized.isBlank()) {
            return chunks;
        }

        StringBuilder current = new StringBuilder();
        for (String paragraph : normalized.split("\\n\\s*\\n")) {
            String trimmed = paragraph.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (trimmed.length() > maxChars) {
                flush(chunks, current);
                chunks.addAll(splitLongText(trimmed, maxChars, overlapChars));
                continue;
            }
            if (current.length() > 0 && current.length() + trimmed.length() + 2 > maxChars) {
                flush(chunks, current);
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(trimmed);
        }
        flush(chunks, current);
        return chunks;
    }

    protected List<String> splitLongText(String text, int maxChars, int overlapChars) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + maxChars);
            chunks.add(text.substring(start, end).trim());
            if (end == text.length()) {
                break;
            }
            start = Math.max(end - overlapChars, start + 1);
        }
        return chunks;
    }

    private void flush(List<String> chunks, StringBuilder current) {
        if (!current.isEmpty()) {
            chunks.add(current.toString().trim());
            current.setLength(0);
        }
    }
}
