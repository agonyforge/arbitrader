package com.r307.arbitrader;

import info.bitrich.xchangestream.core.StreamingExchange;
import org.knowm.xchange.Exchange;

public final class Utils {
    // Intentionally empty
    private Utils() {}

    public static boolean isStreamingExchange(Exchange exchange) {
        return exchange instanceof StreamingExchange;
    }
}
