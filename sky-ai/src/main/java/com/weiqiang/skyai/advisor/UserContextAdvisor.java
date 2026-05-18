package com.weiqiang.skyai.advisor;

import com.weiqiang.skyai.intent_recognition.model.ConfidenceLevel;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.intent_recognition.model.IntentType;
import com.weiqiang.skyai.memory.config.UserProfileMemoryProperties;
import com.weiqiang.skyai.memory.model.MemoryFactKey;
import com.weiqiang.skyai.memory.model.MemoryFactSourceType;
import com.weiqiang.skyai.memory.model.UserMemoryFact;
import com.weiqiang.skyai.memory.service.UserMemoryFactService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Advisor for injecting user context into the prompt based on recognized intents and user memory.
 */
@Slf4j
@Component
public class UserContextAdvisor implements CallAdvisor, StreamAdvisor {

    private final UserMemoryFactService userMemoryFactService;
    private final UserProfileMemoryProperties userProfileMemoryProperties;
    private final UserProfileInjectionMetrics userProfileInjectionMetrics;

    public UserContextAdvisor(UserMemoryFactService userMemoryFactService,
                              UserProfileMemoryProperties userProfileMemoryProperties,
                              UserProfileInjectionMetrics userProfileInjectionMetrics) {
        this.userMemoryFactService = userMemoryFactService;
        this.userProfileMemoryProperties = userProfileMemoryProperties;
        this.userProfileInjectionMetrics = userProfileInjectionMetrics;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        IntentRecognitionResult intentResult = (IntentRecognitionResult) chatClientRequest.context().get("intentResult");
        chatClientRequest.context().put("allowedTools", allowedTools(intentResult));
        String userId = stringParam(chatClientRequest, "userId");
        String contextBlock = buildContextBlock(intentResult, userId);
        if (!StringUtils.hasText(contextBlock)) {
            return callAdvisorChain.nextCall(chatClientRequest);
        }
        List<Message> instructions = new ArrayList<>(chatClientRequest.prompt().getInstructions());
        instructions.add(0, new SystemMessage(contextBlock));
        Prompt prompt = new Prompt(instructions, chatClientRequest.prompt().getOptions());
        return callAdvisorChain.nextCall(chatClientRequest.mutate().prompt(prompt).build());
    }

    @Override
    public reactor.core.publisher.Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        IntentRecognitionResult intentResult = (IntentRecognitionResult) chatClientRequest.context().get("intentResult");
        chatClientRequest.context().put("allowedTools", allowedTools(intentResult));
        String userId = stringParam(chatClientRequest, "userId");
        String contextBlock = buildContextBlock(intentResult, userId);
        if (!StringUtils.hasText(contextBlock)) {
            return streamAdvisorChain.nextStream(chatClientRequest);
        }
        List<Message> instructions = new ArrayList<>(chatClientRequest.prompt().getInstructions());
        instructions.add(0, new SystemMessage(contextBlock));
        Prompt prompt = new Prompt(instructions, chatClientRequest.prompt().getOptions());
        return streamAdvisorChain.nextStream(chatClientRequest.mutate().prompt(prompt).build());
    }

    @Override
    public String getName() {
        return "userContextAdvisor";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    private String buildContextBlock(IntentRecognitionResult intentResult, String userId) {
        IntentType intentType = intentResult == null ? IntentType.OTHER : intentResult.intent();
        ProfileInjectionLevel profileInjectionLevel = profileInjectionLevel(intentType);
        String profileText = buildProfileText(userId, profileInjectionLevel);
        int charsInjected = profileText == null ? 0 : profileText.length();

        if (intentType == IntentType.SHOP_STATUS) {
            userProfileInjectionMetrics.recordContext(intentType, ProfileInjectionLevel.NONE, false, 0);
            return "";
        }

        List<String> parts = new ArrayList<>();
        String memoryBlock = buildRelevantMemoryBlock(userId, profileText, profileInjectionLevel, intentResult);
        if (StringUtils.hasText(memoryBlock)) {
            parts.add(memoryBlock);
        }

        switch (intentType) {
            case ORDER_STATUS, TRACK_DELIVERY -> parts.add(sentence("Order id: " + referencedOrderId(intentResult)));
            case CANCEL_ORDER, REQUEST_REFUND -> parts.add(sentence("Known issues should be treated as persistent operational memory when present."));
            case REPORT_MISSING_ITEM -> parts.add(sentence("Order id: " + referencedOrderId(intentResult)));
            case CHANGE_ADDRESS -> parts.add(sentence("The current user may manage their own saved addresses directly."));
            case MENU_QUERY -> {
                parts.add(sentence("If the user names a dish or setmeal, search the menu first and then act on the unique match directly."));
                parts.add(sentence("Do not ask the user to provide an id when search tools can resolve it."));
                parts.add(sentence("If a unique match is already found, do not repeat the same lookup or re-ask for details."));
            }
            case CART_MANAGEMENT -> {
                parts.add(sentence("The current user may manage their own cart directly."));
                parts.add(sentence("If the user names a dish or setmeal, search the menu first and then add the unique match directly."));
                parts.add(sentence("Do not ask for menu access, do not ask the user to provide an id when search tools can resolve it."));
                parts.add(sentence("If a unique match is already found, do not repeat the same lookup or re-ask for details."));
            }
            case ADDRESS_MANAGEMENT -> {
                parts.add(sentence("The current user may manage their own saved addresses directly."));
                parts.add(sentence("If the user names an address by consignee, phone, label, or detail, search addresses first and then act on the unique match directly."));
                parts.add(sentence("Do not ask the user to provide an id when search tools can resolve it."));
            }
            case ESCALATE_TO_HUMAN -> {
                parts.add(sentence("Order id: " + referencedOrderId(intentResult)));
            }
            case FAQ, OTHER -> {
                // profile memory is already injected through the relevant memory block
            }
            case SHOP_STATUS -> {
                // handled above
            }
        }

        String block = joinSentences(parts);
        boolean profilePresent = StringUtils.hasText(profileText);
        userProfileInjectionMetrics.recordContext(intentType, profileInjectionLevel, profilePresent, charsInjected);
        log.debug("user profile context injection intentType={} level={} profilePresent={} charsInjected={}",
                intentType, profileInjectionLevel, profilePresent, charsInjected);
        return block;
    }

    private ProfileInjectionLevel profileInjectionLevel(IntentType intentType) {
        if (!userProfileMemoryProperties.isEnabled() || intentType == null) {
            return ProfileInjectionLevel.NONE;
        }
        return switch (intentType) {
            case SHOP_STATUS -> ProfileInjectionLevel.NONE;
            case ORDER_STATUS, TRACK_DELIVERY, CANCEL_ORDER, REQUEST_REFUND, CHANGE_ADDRESS, ADDRESS_MANAGEMENT, REPORT_MISSING_ITEM, OTHER ->
                    ProfileInjectionLevel.SUMMARY;
            case MENU_QUERY, CART_MANAGEMENT, FAQ, ESCALATE_TO_HUMAN -> ProfileInjectionLevel.FULL;
        };
    }

    private String buildProfileText(String userId, ProfileInjectionLevel profileInjectionLevel) {
        if (!StringUtils.hasText(userId) || profileInjectionLevel == ProfileInjectionLevel.NONE) {
            return null;
        }
        return switch (profileInjectionLevel) {
            case SUMMARY -> userMemoryFactService.userProfileNotesSummary(userId);
            case FULL -> userMemoryFactService.userProfileNotesDetailed(userId);
            case NONE -> null;
        };
    }

    private String buildRelevantMemoryBlock(String userId,
                                            String profileText,
                                            ProfileInjectionLevel profileInjectionLevel,
                                            IntentRecognitionResult intentResult) {
        if (!StringUtils.hasText(userId)) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        if (StringUtils.hasText(profileText)) {
            lines.add(profileLine(profileText));
        }

        IntentType intentType = intentResult == null ? IntentType.OTHER : intentResult.intent();
        switch (intentType) {
            case ORDER_STATUS, TRACK_DELIVERY, REPORT_MISSING_ITEM, ESCALATE_TO_HUMAN -> {
                // no extra fact lines
            }
            case CANCEL_ORDER, REQUEST_REFUND -> addMemoryLines(lines, userId, List.of(MemoryFactKey.OPERATIONAL_NOTES));
            case CHANGE_ADDRESS, ADDRESS_MANAGEMENT -> addMemoryLines(lines, userId, List.of(MemoryFactKey.DEFAULT_ADDRESS));
            case MENU_QUERY, CART_MANAGEMENT, FAQ -> addMemoryLines(lines, userId, List.of(
                    MemoryFactKey.FAVORITE_DISHES,
                    MemoryFactKey.FAVORITE_FLAVORS,
                    MemoryFactKey.DIETARY_RESTRICTIONS
            ));
            case SHOP_STATUS, OTHER -> {
                // profile only, if present
            }
        }

        if (!lines.isEmpty() && profileInjectionLevel == ProfileInjectionLevel.NONE) {
            // No profile injection for this intent, but still allow existing relevant memory lines.
            return "Relevant memory:\n" + String.join("\n", lines);
        }
        if (!lines.isEmpty()) {
            return "Relevant memory:\n" + String.join("\n", lines);
        }
        return "";
    }

    private void addMemoryLines(List<String> lines, String userId, List<MemoryFactKey> factKeys) {
        if (factKeys == null || factKeys.isEmpty()) {
            return;
        }
        Set<String> keyValues = factKeys.stream().map(MemoryFactKey::value).collect(Collectors.toSet());
        List<UserMemoryFact> facts = userMemoryFactService.findFactsSorted(userId).stream()
                .filter(fact -> keyValues.contains(fact.getFactKey()))
                .toList();
        for (UserMemoryFact fact : facts) {
            String line = formatMemoryLine(fact);
            if (StringUtils.hasText(line)) {
                lines.add(line);
            }
        }
    }

    private String profileLine(String profileText) {
        return "- User profile notes: " + profileText.trim();
    }

    private Set<String> allowedTools(IntentRecognitionResult intentResult) {
        if (intentResult == null) {
            return Set.of();
        }
        return switch (intentResult.intent()) {
            case ORDER_STATUS, TRACK_DELIVERY -> setOf("searchOrders", "getOrderDetail", "listRecentOrders", "remindOrder");
            case CANCEL_ORDER -> setOf("searchOrders", "cancelOrder");
            case REQUEST_REFUND -> setOf("searchOrders", "requestRefund");
            case CHANGE_ADDRESS -> setOf("searchOrders", "searchAddresses", "updateDeliveryAddress");
            case REPORT_MISSING_ITEM -> setOf("searchOrders", "getOrderDetail", "requestRefund");
            case MENU_QUERY -> setOf("searchDishes", "searchSetmeals", "listCategories", "listDishesByCategory", "listSetmealsByCategory", "listSetmealDishes", "getShopStatus");
            case CART_MANAGEMENT -> setOf("searchDishes", "searchSetmeals", "searchCartItems", "listCart", "addDishToCart", "addSetmealToCart", "removeCartItem", "cleanCart");
            case ADDRESS_MANAGEMENT -> setOf("searchAddresses", "listAddresses", "getDefaultAddress", "setDefaultAddress", "updateAddress");
            case SHOP_STATUS -> setOf("getShopStatus");
            case FAQ, ESCALATE_TO_HUMAN, OTHER -> Set.of();
        };
    }

    private Set<String> setOf(String... tools) {
        return new LinkedHashSet<>(List.of(tools));
    }

    private String referencedOrderId(IntentRecognitionResult intentResult) {
        String orderId = intentResult == null || intentResult.entities() == null ? null : intentResult.entities().get("order_id");
        return StringUtils.hasText(orderId) ? orderId : "unavailable";
    }

    private String joinSentences(List<String> parts) {
        return parts.stream().filter(StringUtils::hasText).limit(5).reduce((left, right) -> left + " " + right).orElse("");
    }

    private String sentence(String text) {
        String value = StringUtils.hasText(text) ? text.trim() : "unavailable";
        return value.endsWith(".") ? value : value + ".";
    }

    private String formatMemoryLine(UserMemoryFact fact) {
        if (fact == null || !StringUtils.hasText(fact.getFactValue())) {
            return "";
        }
        String label = memoryLabel(fact.getFactKey());
        String suffix = fact.getSourceType() == MemoryFactSourceType.USER_MANUAL ? " [用户自设]" : "";
        return "- " + label + ": " + fact.getFactValue().trim() + suffix;
    }

    private String memoryLabel(String factKey) {
        if (MemoryFactKey.FAVORITE_DISHES.value().equals(factKey)) {
            return "Favorite dishes";
        }
        if (MemoryFactKey.FAVORITE_FLAVORS.value().equals(factKey)) {
            return "Favorite flavors";
        }
        if (MemoryFactKey.DIETARY_RESTRICTIONS.value().equals(factKey)) {
            return "Dietary restrictions";
        }
        if (MemoryFactKey.DEFAULT_ADDRESS.value().equals(factKey)) {
            return "Default address";
        }
        if (MemoryFactKey.OPERATIONAL_NOTES.value().equals(factKey)) {
            return "Operational notes";
        }
        if (MemoryFactKey.USER_PROFILE_NOTES.value().equals(factKey)) {
            return "User profile notes";
        }
        return factKey;
    }

    private String stringParam(ChatClientRequest request, String key) {
        Object value = request.context().get(key);
        return value instanceof String stringValue && StringUtils.hasText(stringValue) ? stringValue : null;
    }
}
