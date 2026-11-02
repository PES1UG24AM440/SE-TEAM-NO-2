package com.bookstore.orders;

import java.util.Map;

public class OrderException extends RuntimeException {

    private final int status;
    private final String code;
    private final Map<String, Object> extra;

    public OrderException(int status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public OrderException(
            int status,
            String code,
            String message,
            Map<String, Object> extra) {
        super(message);
        this.status = status;
        this.code = code;
        this.extra = extra;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }
}