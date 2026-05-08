package com.weiqiang.skyai.rag.offline.chunk;

import com.weiqiang.skyai.rag.offline.model.DocumentType;
import com.weiqiang.skyai.rag.offline.model.ParsedDocument;
import com.weiqiang.skyai.rag.offline.model.RagChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class MarkdownChunkingStrategy extends AbstractChunkingStrategy {

    @Override
    public DocumentType supports() {
        return DocumentType.MARKDOWN;
    }

    @Override
    public List<RagChunk> chunk(ParsedDocument document) {
        List<Section> sections = parseSections(document.content());
        List<RagChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;
        for (Section section : sections) {
            String prefix = section.path().isEmpty() ? "" : String.join(" > ", section.path()) + "\n\n";
            for (String part : splitByParagraphThenLength(section.content(), DEFAULT_MAX_CHARS, DEFAULT_OVERLAP_CHARS)) {
                Map<String, Object> metadata = baseMetadata(document);
                metadata.put("title", section.path().isEmpty() ? document.sourceName() : section.path().get(section.path().size() - 1));
                metadata.put("sectionPath", section.path());
                metadata.put("levelDepth", section.path().size());
                chunks.add(RagChunk.of(document, chunkIndex++, prefix + part, metadata));
            }
        }
        return chunks;
    }

    private List<Section> parseSections(String markdown) {
        List<Section> sections = new ArrayList<>();
        List<String> headingStack = new ArrayList<>();
        List<String> currentPath = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String line : (markdown == null ? "" : markdown).replace("\r\n", "\n").split("\n")) {
            Heading heading = parseHeading(line);
            if (heading != null) {
                addSection(sections, currentPath, current);
                while (headingStack.size() >= heading.level()) {
                    headingStack.remove(headingStack.size() - 1);
                }
                headingStack.add(heading.title());
                currentPath = new ArrayList<>(headingStack);
                current.append(line).append('\n');
            } else {
                current.append(line).append('\n');
            }
        }
        addSection(sections, currentPath, current);
        if (sections.isEmpty() && markdown != null && !markdown.isBlank()) {
            sections.add(new Section(List.of(), markdown));
        }
        return sections;
    }

    private Heading parseHeading(String line) {
        if (line == null || !line.startsWith("#")) {
            return null;
        }
        int level = 0;
        while (level < line.length() && line.charAt(level) == '#') {
            level++;
        }
        if (level == 0 || level > 6 || level >= line.length() || line.charAt(level) != ' ') {
            return null;
        }
        return new Heading(level, line.substring(level + 1).trim());
    }

    private void addSection(List<Section> sections, List<String> path, StringBuilder content) {
        String text = content.toString().trim();
        if (!text.isBlank()) {
            sections.add(new Section(List.copyOf(path), text));
        }
        content.setLength(0);
    }

    private record Heading(int level, String title) {
    }

    private record Section(List<String> path, String content) {
    }
}
