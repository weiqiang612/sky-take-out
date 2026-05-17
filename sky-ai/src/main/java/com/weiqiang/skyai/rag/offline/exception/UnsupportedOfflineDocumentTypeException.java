package com.weiqiang.skyai.rag.offline.exception;

public class UnsupportedOfflineDocumentTypeException extends RuntimeException {

    public UnsupportedOfflineDocumentTypeException(String sourceName) {
        super(buildMessage(sourceName));
    }

    public UnsupportedOfflineDocumentTypeException(String sourceName, Throwable cause) {
        super(buildMessage(sourceName), cause);
    }

    private static String buildMessage(String sourceName) {
        return "当前上传的文档格式不支持，请上传 PDF、Markdown、TXT 或 QA(JSONL) 文件: " + sourceName;
    }
}
