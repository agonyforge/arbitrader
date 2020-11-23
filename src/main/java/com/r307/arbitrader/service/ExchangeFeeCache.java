package com.r307.arbitrader.service;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Component
public class ExchangeFeeCache {
    private final Map<String, BigDecimal> cache = new HashMap<>();

    public BigDecimal getCachedFee(Exchange exchange, CurrencyPair currencyPair) {
        return cache.get(computeCacheKey(exchange, currencyPair));
    }

    public void setCachedFee(Exchange exchange, CurrencyPair currencyPair, BigDecimal fee) {
        cache.put(computeCacheKey(exchange, currencyPair), fee);
    }

    private String computeCacheKey(Exchange exchange, CurrencyPair currencyPair) {
        return String.format("%s:%s",
            exchange.getExchangeSpecification().getExchangeName(),
            currencyPair.toString());
    }
}
