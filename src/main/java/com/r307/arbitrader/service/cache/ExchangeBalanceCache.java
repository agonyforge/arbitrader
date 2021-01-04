package com.r307.arbitrader.service.cache;

import org.knowm.xchange.Exchange;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Cache account balances to avoid rate limiting. Balances do change pretty frequently so we
 * don't cache them for long, but we can avoid some repetitive calls without risking incorrect
 * information.
 */
public class ExchangeBalanceCache {
    public static final long CACHE_TIMEOUT = 1000 * 60; // 1 minute

    private final Map<Exchange, AccountBalance> cache = new HashMap<>();

    public BigDecimal getCachedBalance(Exchange exchange) {
        AccountBalance balance = cache.get(exchange);

        if (balance == null || System.currentTimeMillis() - balance.getTimestamp() > CACHE_TIMEOUT) {
            return null;
        }

        return balance.getAmount();
    }

    public void setCachedBalance(Exchange exchange, BigDecimal amount) {
        setCachedBalance(exchange, amount, System.currentTimeMillis());
    }

    // intended for testing so that you can set your own timestamp
    // if you want to test that cached items "expire" correctly
    void setCachedBalance(Exchange exchange, BigDecimal amount, long timestamp) {
        AccountBalance balance = new AccountBalance(amount, timestamp);

        cache.put(exchange, balance);
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
