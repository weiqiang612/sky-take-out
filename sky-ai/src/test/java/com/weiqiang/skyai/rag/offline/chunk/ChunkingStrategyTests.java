package com.weiqiang.skyai.rag.offline.chunk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiqiang.skyai.rag.offline.model.DocumentType;
import com.weiqiang.skyai.rag.offline.model.ParsedDocument;
import com.weiqiang.skyai.rag.offline.model.RagChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkingStrategyTests {

    @Test
    void qaKeepsQuestionWithEveryLongAnswerChunk() {
        String longAnswer = "answer ".repeat(500);
        ParsedDocument document = new ParsedDocument("doc-1", "v1", DocumentType.QA, "qa.jsonl",
                "{\"question\":\"How to refund?\",\"answer\":\"" + longAnswer + "\",\"category\":\"order\",\"relatedId\":\"qa-1\"}");

        List<RagChunk> chunks = new QaChunkingStrategy(new ObjectMapper()).chunk(document);

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.content().startsWith("Question: How to refund?\nAnswer: ")));
        assertEquals("order", chunks.get(0).metadata().get("category"));
        assertEquals("qa-1", chunks.get(0).metadata().get("relatedId"));
    }

    @Test
    void markdownKeepsSectionPathAndPrefixesContent() {
        ParsedDocument document = new ParsedDocument("doc-1", "v1", DocumentType.MARKDOWN, "guide.md",
                "# Root\nIntro\n\n## Install\nRun mvn test.");

        List<RagChunk> chunks = new MarkdownChunkingStrategy().chunk(document);

        RagChunk installChunk = chunks.stream()
                .filter(chunk -> chunk.metadata().get("sectionPath").toString().contains("Install"))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("Root", "Install"), installChunk.metadata().get("sectionPath"));
        assertTrue(installChunk.content().startsWith("Root > Install"));
    }

    @Test
    void txtSplitsByParagraphBeforeLengthFallback() {
        ParsedDocument document = new ParsedDocument("doc-1", "v1", DocumentType.TXT, "plain.txt",
                "First paragraph.\n\nSecond paragraph.");

        List<RagChunk> chunks = new TxtChunkingStrategy().chunk(document);

        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).content().contains("First paragraph."));
        assertEquals("plain.txt", chunks.get(0).metadata().get("fileName"));
    }
}
