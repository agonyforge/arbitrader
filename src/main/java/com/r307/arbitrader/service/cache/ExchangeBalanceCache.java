package com.r307.arbitrader.service.cache;

import org.knowm.xchange.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Cache account balances to avoid rate limiting. Balances do change pretty frequently so we
 * don't cache them for long, but we can avoid some repetitive calls without risking incorrect
 * information.
 */
public class ExchangeBalanceCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExchangeBalanceCache.class);

    public static final long CACHE_TIMEOUT = 1000 * 60; // 1 minute

    private final Map<Exchange, AccountBalance> cache = new HashMap<>();

    /**
     * Retrieve a balance from the cache.
     *
     * @param exchange The exchange to retrieve a balance for.
     * @return The account balance for the requested exchange.
     */
    public Optional<BigDecimal> getCachedBalance(Exchange exchange) {
        AccountBalance balance = cache.get(exchange);

        if (balance == null) {
            LOGGER.debug("Cache did not contain a value for exchange {}", exchange.getExchangeSpecification().getExchangeName());
            return Optional.empty();
        }

        if (System.currentTimeMillis() - balance.getTimestamp() > CACHE_TIMEOUT) {
            LOGGER.debug("Cache had an expired value for exchange {}", exchange.getExchangeSpecification().getExchangeName());
            return Optional.empty();
        }

        LOGGER.debug("Cache returned a cached value for exchange {}", exchange.getExchangeSpecification().getExchangeName());
        return Optional.of(balance.getAmount());
    }

    /**
     * Put a balance into the cache.
     *
     * @param exchange The exchange the balance is associated with.
     * @param amount The amount of the account balance.
     */
    public void setCachedBalance(Exchange exchange, BigDecimal amount) {
        setCachedBalance(exchange, amount, System.currentTimeMillis());
    }

    // intended for testing so that you can set your own timestamp
    // if you want to test that cached items "expire" correctly without
    // actually waiting for them to expire
    void setCachedBalance(Exchange exchange, BigDecimal amount, long timestamp) {
        AccountBalance balance = new AccountBalance(amount, timestamp);

        LOGGER.debug("Caching new value: {} -> {}", exchange.getExchangeSpecification().getExchangeName(), amount);
        cache.put(exchange, balance);
    }

    /**
     * Remove a cached value, if any exists, for the given exchanges.
     * This method is useful if we have taken some action such as executing a trade and
     * we know for sure that the account balance has changed.
     *
     * @param exchanges The exchanges to invalidate.
     */
    public void invalidate(Exchange ... exchanges) {
        if (LOGGER.isDebugEnabled()) { // avoid the stream/map/collect if DEBUG is turned off
            String exchangeNames = Arrays
                .stream(exchanges)
                .map(exchange -> exchange.getExchangeSpecification().getExchangeName())
                .collect(Collectors.joining(", "));

            LOGGER.debug("Cache invalidating exchanges: {}", exchangeNames);
        }

        Arrays.stream(exchanges).forEach(cache::remove);
    }

    private static class AccountBalance {
        private final BigDecimal amount;
        private final long timestamp;

        public AccountBalance(BigDecimal amount, long timestamp) {
            this.amount = amount;
            this.timestamp = timestamp;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}
