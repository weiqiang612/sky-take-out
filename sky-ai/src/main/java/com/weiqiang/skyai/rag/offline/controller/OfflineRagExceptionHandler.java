package com.weiqiang.skyai.rag.offline.controller;

import com.weiqiang.skyai.rag.offline.exception.OfflineIndexProcessingException;
import com.weiqiang.skyai.rag.offline.exception.UnsupportedOfflineDocumentTypeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = OfflineRagController.class)
public class OfflineRagExceptionHandler {

    @ExceptionHandler(UnsupportedOfflineDocumentTypeException.class)
    public ResponseEntity<OfflineIndexErrorResponse> handleUnsupportedDocumentType(UnsupportedOfflineDocumentTypeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new OfflineIndexErrorResponse("UNSUPPORTED_DOCUMENT_TYPE", ex.getMessage()));
    }

    @ExceptionHandler(OfflineIndexProcessingException.class)
    public ResponseEntity<OfflineIndexErrorResponse> handleProcessingFailure(OfflineIndexProcessingException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new OfflineIndexErrorResponse("OFFLINE_INDEX_FAILED", ex.getMessage()));
    }
}
