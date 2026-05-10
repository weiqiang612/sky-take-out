package com.weiqiang.skyai.memory.advisor;

import com.weiqiang.skyai.intent_recognition.model.ConfidenceLevel;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.intent_recognition.model.IntentType;
import com.weiqiang.skyai.memory.model.UserMemory;
import com.weiqiang.skyai.memory.repository.UserMemoryRepository;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Advisor for injecting user context into the prompt based on recognized intents and user memory.
 */
@Component
public class UserContextAdvisor implements CallAdvisor {

    private final UserMemoryRepository userMemoryRepository;

    public UserContextAdvisor(UserMemoryRepository userMemoryRepository) {
        this.userMemoryRepository = userMemoryRepository;
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
    public String getName() {
        return "userContextAdvisor";
    }

    // Ensure this advisor runs early, right after intent recognition, to inject context before other advisors or the model processes the prompt
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    private String buildContextBlock(IntentRecognitionResult intentResult, String userId) {
        if (intentResult == null) {
            intentResult = new IntentRecognitionResult(IntentType.OTHER, ConfidenceLevel.LOW, Map.of(), List.of(), null, false, null);
        }
        if (intentResult.intent() == IntentType.OTHER && intentResult.confidence() == ConfidenceLevel.LOW) {
            return "";
        }
        UserMemory userMemory = StringUtils.hasText(userId) ? userMemoryRepository.findById(userId).orElse(null) : null;
        return switch (intentResult.intent()) {
            case ORDER_STATUS, TRACK_DELIVERY -> sentence("Order id: " + referencedOrderId(intentResult));
            case CANCEL_ORDER, REQUEST_REFUND -> sentence("Known issues: " + memoryValue(userMemory, UserMemory::getKnownIssues));
            case REPORT_MISSING_ITEM -> sentence("Order id: " + referencedOrderId(intentResult));
            case CHANGE_ADDRESS -> sentence("Default address: " + memoryValue(userMemory, UserMemory::getDefaultAddress));
            case MENU_QUERY -> joinSentences(List.of(
                    sentence("If the user names a dish or setmeal, search the menu first and then act on the unique match directly."),
                    sentence("Do not ask the user to provide an id when search tools can resolve it.")
            ));
            case CART_MANAGEMENT -> joinSentences(List.of(
                    sentence("The current user may manage their own cart directly."),
                    sentence("If the user names a dish or setmeal, search the menu first and then add the unique match directly."),
                    sentence("Do not ask for menu access, do not ask the user to provide an id when search tools can resolve it.")
            ));
            case ADDRESS_MANAGEMENT -> joinSentences(List.of(
                    sentence("The current user may manage their own saved addresses directly."),
                    sentence("If the user names an address by consignee, phone, label, or detail, search addresses first and then act on the unique match directly."),
                    sentence("Do not ask the user to provide an id when search tools can resolve it.")
            ));
            case SHOP_STATUS -> sentence("You may check the shop status directly.");
            case ESCALATE_TO_HUMAN -> joinSentences(List.of(
                    sentence("Order id: " + referencedOrderId(intentResult)),
                    sentence("Known issues: " + memoryValue(userMemory, UserMemory::getKnownIssues))
            ));
            case FAQ -> sentence("Dietary preferences: " + memoryValue(userMemory, UserMemory::getDietaryPrefs));
            case OTHER -> sentence("Default address: " + memoryValue(userMemory, UserMemory::getDefaultAddress));
        };
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
        String orderId = intentResult.entities() == null ? null : intentResult.entities().get("order_id");
        return StringUtils.hasText(orderId) ? orderId : "unavailable";
    }

    private String memoryValue(UserMemory userMemory, java.util.function.Function<UserMemory, String> extractor) {
        if (userMemory == null) {
            return "unavailable";
        }
        String value = extractor.apply(userMemory);
        return StringUtils.hasText(value) ? oneSentence(value) : "unavailable";
    }

    private String joinSentences(List<String> parts) {
        return parts.stream().filter(StringUtils::hasText).limit(5).reduce((left, right) -> left + " " + right).orElse("");
    }

    private String sentence(String text) {
        String value = StringUtils.hasText(text) ? text.trim() : "unavailable";
        return value.endsWith(".") ? value : value + ".";
    }

    private String oneSentence(String text) {
        int end = text.indexOf('.');
        return end >= 0 ? text.substring(0, end + 1).trim() : text.trim();
    }

    private String stringParam(ChatClientRequest request, String key) {
        Object value = request.context().get(key);
        return value instanceof String stringValue && StringUtils.hasText(stringValue) ? stringValue : null;
    }
}
