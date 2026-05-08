package com.weiqiang.skyai.rag.offline.controller;

import com.weiqiang.skyai.rag.offline.index.OfflineIndexService;
import com.weiqiang.skyai.rag.offline.model.OfflineIndexResponse;
import com.weiqiang.skyai.rag.offline.model.RagDocumentSummary;
import com.weiqiang.skyai.rag.offline.store.RagIndexRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/rag/offline")
@RequiredArgsConstructor
public class OfflineRagController {

    private final OfflineIndexService offlineIndexService;
    private final RagIndexRepository repository;

    @PostMapping("/index")
    public OfflineIndexResponse index(@RequestParam("file") MultipartFile file,
                                      @RequestParam(value = "documentType", required = false) String documentType) {
        return offlineIndexService.index(file, documentType);
    }

    @GetMapping("/documents")
    public List<RagDocumentSummary> documents() {
        return repository.findDocuments();
    }
}
