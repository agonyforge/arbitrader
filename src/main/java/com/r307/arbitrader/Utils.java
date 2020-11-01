package com.r307.arbitrader;

import org.knowm.xchange.Exchange;

public final class Utils {
    // Intentionally empty
    private Utils() {}

    public static boolean isStreamingExchange(Exchange exchange) {
        return exchange.getExchangeSpecification().getExchangeClassName().contains("Streaming");
    }
}
