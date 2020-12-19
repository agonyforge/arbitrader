package com.r307.arbitrader.exception;

/**
 * A RuntimeException thrown when an order could not be found on an Exchange.
 */
public class OrderNotFoundException extends RuntimeException {
    private final String orderId;

    public OrderNotFoundException(String orderId) {
        super("Order ID " + orderId + " not found on exchange.");
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
