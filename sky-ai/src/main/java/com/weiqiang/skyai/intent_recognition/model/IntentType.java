package com.weiqiang.skyai.intent_recognition.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum IntentType {
    ORDER_STATUS("order_status"),
    CANCEL_ORDER("cancel_order"),
    REQUEST_REFUND("request_refund"),
    TRACK_DELIVERY("track_delivery"),
    REPORT_MISSING_ITEM("report_missing_item"),
    CHANGE_ADDRESS("change_address"),
    MENU_QUERY("menu_query"),
    CART_MANAGEMENT("cart_management"),
    ADDRESS_MANAGEMENT("address_management"),
    SHOP_STATUS("shop_status"),
    FAQ("faq"),
    ESCALATE_TO_HUMAN("escalate_to_human"),
    OTHER("other");

    private final String value;

    IntentType(String value) {
        this.value = value;
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
}
