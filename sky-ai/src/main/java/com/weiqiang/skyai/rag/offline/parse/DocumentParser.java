package com.weiqiang.skyai.rag.offline.parse;

import com.weiqiang.skyai.rag.offline.model.DocumentType;
import com.weiqiang.skyai.rag.offline.model.ParsedDocument;
import com.weiqiang.skyai.rag.offline.exception.UnsupportedOfflineDocumentTypeException;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@Component
public class DocumentParser {

    private final Tika tika = new Tika();

    public ParsedDocument parse(MultipartFile file, DocumentType explicitType, String documentId, String indexVersion) {
        String sourceName = file.getOriginalFilename() == null ? "uploaded-document" : file.getOriginalFilename();
        DocumentType documentType = explicitType == null ? DocumentType.infer(sourceName) : explicitType;
        if (documentType == null) {
            throw new UnsupportedOfflineDocumentTypeException(sourceName);
        }
        try {
            String content = switch (documentType) {
                case PDF -> tika.parseToString(file.getInputStream());
                case QA, MARKDOWN, TXT -> new String(file.getBytes(), StandardCharsets.UTF_8);
            };
            return new ParsedDocument(documentId, indexVersion, documentType, sourceName, content);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse document: " + sourceName, ex);
        }
    }
}
