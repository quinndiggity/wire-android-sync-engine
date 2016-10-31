package com.waz.model.sync;

import java.util.HashMap;
import java.util.Map;

public enum ReceiptType {

    Delivery("delivery"),
    EphemeralExpired("ephemeral-expired");

    public final String name;

    ReceiptType(String name) {
        this.name = name;
    }

    private static Map<String, ReceiptType> byName = new HashMap<>();
    static {
        for (ReceiptType value : ReceiptType.values()) {
            byName.put(value.name, value);
        }
    }

    public static ReceiptType fromName(String name) {
        ReceiptType result = byName.get(name);
        return (result == null) ? Delivery : result;
    }
}
