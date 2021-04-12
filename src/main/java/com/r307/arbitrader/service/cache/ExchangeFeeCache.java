package com.r307.arbitrader.service.cache;

import com.r307.arbitrader.service.model.ExchangeFee;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Cache exchange fee amounts. They don't change that often and we request them frequently,
 * so this saves us from a lot of API rate limiting.
 */
@Component
public class ExchangeFeeCache {
    private final Map<String, ExchangeFee> cache = new HashMap<>();

    /**
     * Return a fee from the cache.
     *
     * @param exchange The Exchange to fetch a fee from.
     * @param currencyPair The CurrencyPair to fetch a fee from.
     * @return The fee as a decimal such as 0.0016, or 0.16%
     */
    public Optional<ExchangeFee> getCachedFee(Exchange exchange, CurrencyPair currencyPair) {
        return Optional.ofNullable(cache.get(computeCacheKey(exchange, currencyPair)));
    }

    /**
     * Include a fee in the cache.
     *
     * @param exchange The Exchange this fee comes from.
     * @param currencyPair The CurrencyPair this fee is for.
     * @param fee The fee as a decimal, such as 0.0016 for 0.16%
     */
    public void setCachedFee(Exchange exchange, CurrencyPair currencyPair, ExchangeFee fee) {
        cache.put(computeCacheKey(exchange, currencyPair), fee);
    }

    // generate a string that represents an exchange and currency pair, suitable for use as a key in a Map
    private String computeCacheKey(Exchange exchange, CurrencyPair currencyPair) {
        return String.format("%s:%s",
            exchange.getExchangeSpecification().getExchangeName(),
            currencyPair.toString());
    }
}
