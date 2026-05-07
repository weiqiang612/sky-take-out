package com.weiqiang.skyai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final VectorStore vectorStore;

    @Value("classpath:/rag/demo-document.txt")
    private Resource demoDocument;

    public int ingestDemoDocument() {
        List<Document> documents = new TikaDocumentReader(demoDocument).read();
        vectorStore.add(documents);
        return documents.size();
    }
}
