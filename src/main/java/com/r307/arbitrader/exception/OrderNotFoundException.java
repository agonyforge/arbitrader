package com.r307.arbitrader.exception;

import org.knowm.xchange.Exchange;

/**
 * A RuntimeException thrown when an order could not be found on an Exchange.
 */
public class OrderNotFoundException extends RuntimeException {
    private final Exchange exchange;
    private final String orderId;

    public OrderNotFoundException(Exchange exchange, String orderId) {
        super("Order ID " + orderId + " not found on " + exchange.getExchangeSpecification().getExchangeName() + ".");

        this.exchange = exchange;
        this.orderId = orderId;
    }

    public Exchange getExchange() {
        return exchange;
    }

    public String getOrderId() {
        return orderId;
    }
}
