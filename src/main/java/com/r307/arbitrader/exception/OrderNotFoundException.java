package com.r307.arbitrader.exception;

public class OrderNotFoundException extends RuntimeException {
    private String orderId;

    public OrderNotFoundException(String orderId) {
        super("Order ID " + orderId + " not found on exchange.");
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
