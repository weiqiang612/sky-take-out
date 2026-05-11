package com.weiqiang.skyai.memory.service;

import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.weiqiang.skyai.memory.repository.RedisChatMemoryRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChatHistoryService {

    private static final Pattern ORDER_ID_PATTERN = Pattern.compile("order[_\\s-]?id[:=\\s]+([a-zA-Z0-9-]+)", Pattern.CASE_INSENSITIVE);

    private final RedisChatMemoryRepository redisChatMemoryRepository;
    private final UserMemoryFactService userMemoryFactService;

    public ChatHistoryService(RedisChatMemoryRepository redisChatMemoryRepository, UserMemoryFactService userMemoryFactService) {
        this.redisChatMemoryRepository = redisChatMemoryRepository;
        this.userMemoryFactService = userMemoryFactService;
    }

    public List<String> buildHistory(String conversationId, String userId) {
        List<String> history = new ArrayList<>();
        List<Message> messages = redisChatMemoryRepository.findByConversationId(conversationId);
        int start = Math.max(0, messages.size() - 4);
        for (int i = start; i < messages.size(); i++) {
            history.add(messages.get(i).getMessageType() + ": " + safeText(messages.get(i).getText()));
        }
        String orderId = extractOrderId(messages);
        if (StringUtils.hasText(orderId)) {
            history.add("Known order id: " + orderId);
        }
        if (StringUtils.hasText(userId)) {
            String knownIssues = userMemoryFactService.operationalNotesSummary(userId);
            if (StringUtils.hasText(knownIssues)) {
                history.add("Known issues: " + oneSentence(knownIssues));
            }
        }
        return history;
    }

    private String extractOrderId(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Matcher matcher = ORDER_ID_PATTERN.matcher(safeText(messages.get(i).getText()));
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private String oneSentence(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String value = text.trim();
        int end = value.indexOf('。');
        if (end >= 0) {
            return value.substring(0, end + 1).trim();
        }
        end = value.indexOf('.');
        return end >= 0 ? value.substring(0, end + 1).trim() : value.trim();
    }

    private String safeText(String text) {
        return StringUtils.hasText(text) ? text.trim() : "";
    }
}

