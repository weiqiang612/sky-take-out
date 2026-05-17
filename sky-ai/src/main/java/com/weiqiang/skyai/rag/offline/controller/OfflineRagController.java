package com.weiqiang.skyai.rag.offline.controller;

import com.weiqiang.skyai.rag.offline.index.OfflineIndexService;
import com.weiqiang.skyai.rag.offline.model.OfflineIndexResponse;
import com.weiqiang.skyai.rag.offline.model.RagDocumentOperationResponse;
import com.weiqiang.skyai.rag.offline.model.RagDocumentIdsRequest;
import com.weiqiang.skyai.rag.offline.model.RagDocumentSummary;
import com.weiqiang.skyai.rag.offline.store.RagIndexRepository;
import com.weiqiang.skyai.rag.offline.service.OfflineRagDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/rag/offline")
@RequiredArgsConstructor
public class OfflineRagController {

    private final OfflineIndexService offlineIndexService;
    private final OfflineRagDocumentService documentService;
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

    @PostMapping("/documents/disable")
    public RagDocumentOperationResponse disableDocuments(@RequestBody RagDocumentIdsRequest request) {
        List<String> documentIds = request == null ? List.of() : request.documentIds();
        int affectedCount = documentService.disableDocuments(documentIds);
        return new RagDocumentOperationResponse("disable", documentIds, affectedCount);
    }

    @PostMapping("/documents/{documentId}/disable")
    public RagDocumentOperationResponse disableDocument(@PathVariable String documentId) {
        return disableDocuments(new RagDocumentIdsRequest(List.of(documentId)));
    }

    @PostMapping("/documents/enable")
    public RagDocumentOperationResponse enableDocuments(@RequestBody RagDocumentIdsRequest request) {
        List<String> documentIds = request == null ? List.of() : request.documentIds();
        int affectedCount = documentService.enableDocuments(documentIds);
        return new RagDocumentOperationResponse("enable", documentIds, affectedCount);
    }

    @PostMapping("/documents/{documentId}/enable")
    public RagDocumentOperationResponse enableDocument(@PathVariable String documentId) {
        return enableDocuments(new RagDocumentIdsRequest(List.of(documentId)));
    }

    @DeleteMapping("/documents")
    public RagDocumentOperationResponse deleteDocuments(@RequestBody RagDocumentIdsRequest request) {
        List<String> documentIds = request == null ? List.of() : request.documentIds();
        int affectedCount = documentService.deleteDocuments(documentIds);
        return new RagDocumentOperationResponse("delete", documentIds, affectedCount);
    }

    @DeleteMapping("/documents/{documentId}")
    public RagDocumentOperationResponse deleteDocument(@PathVariable String documentId) {
        return deleteDocuments(new RagDocumentIdsRequest(List.of(documentId)));
    }
}
