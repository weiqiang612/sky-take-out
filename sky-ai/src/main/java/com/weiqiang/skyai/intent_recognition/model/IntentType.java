package com.weiqiang.skyai.intent_recognition.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum IntentType {
    ORDER_STATUS("order_status", false),
    CANCEL_ORDER("cancel_order", true),
    REQUEST_REFUND("request_refund", true),
    TRACK_DELIVERY("track_delivery", false),
    REPORT_MISSING_ITEM("report_missing_item", true),
    CHANGE_ADDRESS("change_address", true),
    MENU_QUERY("menu_query", false),
    CART_MANAGEMENT("cart_management", false),
    ADDRESS_MANAGEMENT("address_management", false),
    SHOP_STATUS("shop_status", false),
    FAQ("faq", false),
    ESCALATE_TO_HUMAN("escalate_to_human", false),
    OTHER("other", false);

    private final String value;
    private final boolean highRisk;

    IntentType(String value, boolean highRisk) {
        this.value = value;
        this.highRisk = highRisk;
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
}
