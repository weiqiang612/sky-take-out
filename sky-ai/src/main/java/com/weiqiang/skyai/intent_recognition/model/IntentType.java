package com.weiqiang.skyai.intent_recognition.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum IntentType {
    // ORDER
    ORDER_STATUS("order_status", false, IntentCategory.TASK, TaskDomain.ORDER),
    CANCEL_ORDER("cancel_order", true, IntentCategory.TASK, TaskDomain.ORDER),
    REQUEST_REFUND("request_refund", true, IntentCategory.TASK, TaskDomain.ORDER),
    REORDER("reorder", false , IntentCategory.TASK, TaskDomain.ORDER),
    TRACK_DELIVERY("track_delivery", false, IntentCategory.TASK, TaskDomain.ORDER),
    REPORT_MISSING_ITEM("report_missing_item", true, IntentCategory.TASK, TaskDomain.ORDER),

    // ADDRESS
    CHANGE_ADDRESS("change_address", true , IntentCategory.TASK, TaskDomain.ADDRESS),
    ADDRESS_MANAGEMENT("address_management", false, IntentCategory.TASK, TaskDomain.ADDRESS),

    // MENU
    MENU_QUERY("menu_query", false , IntentCategory.TASK, TaskDomain.MENU),
    CART_MANAGEMENT("cart_management", false, IntentCategory.TASK, TaskDomain.MENU),

    // SHOP
    SHOP_STATUS("shop_status", false, IntentCategory.TASK, TaskDomain.SHOP),
    ESCALATE_TO_HUMAN("escalate_to_human", false, IntentCategory.CONVERSATIONAL, TaskDomain.SHOP),

    // NULL,
    FAQ("faq", false, IntentCategory.KNOWLEDGE, null),
    OTHER("other", false, IntentCategory.CONVERSATIONAL, null);

    private final String value;
    private final boolean highRisk;
    private final IntentCategory category;
    private final TaskDomain domain;

    IntentType(String value, boolean highRisk, IntentCategory category, TaskDomain domain) {
        this.value = value;
        this.highRisk = highRisk;
        this.category = category;
        this.domain = domain;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static IntentType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (IntentType type : values()) {
            if (type.value.equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown intent type: " + value);
    }

    public boolean isHighRisk() {
        return highRisk;
    }

    public boolean isTask()           { return category == IntentCategory.TASK; }
    public boolean isKnowledge()      { return category == IntentCategory.KNOWLEDGE; }
    public boolean isConversational() { return category == IntentCategory.CONVERSATIONAL; }
    public IntentCategory category()  { return category; }
    public TaskDomain domain()        { return domain; }
}
