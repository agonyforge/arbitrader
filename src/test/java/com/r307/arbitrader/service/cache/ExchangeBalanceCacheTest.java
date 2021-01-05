package com.r307.arbitrader.service.cache;

import com.r307.arbitrader.BaseTestCase;
import com.r307.arbitrader.ExchangeBuilder;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class ExchangeBalanceCacheTest extends BaseTestCase {
    private Exchange exchangeA;
    private Exchange exchangeB;

    private ExchangeBalanceCache cache;

    @Before
    public void setUp() throws IOException {
        exchangeA = new ExchangeBuilder("CoinDynasty", CurrencyPair.BTC_USD)
            .build();

        exchangeB = new ExchangeBuilder("CoinSnake", CurrencyPair.BTC_USD)
            .build();

        cache = new ExchangeBalanceCache();
    }

    @Test
    public void testGetCachedBalance() {
        BigDecimal value = new BigDecimal("123.45");

        cache.setCachedBalance(exchangeA, value);

        assertEquals(Optional.of(value), cache.getCachedBalance(exchangeA));
    }

    @Test
    public void testGetCachedBalances() {
        BigDecimal valueA = new BigDecimal("123.45");
        BigDecimal valueB = new BigDecimal("987.65");

        cache.setCachedBalance(exchangeA, valueA);
        cache.setCachedBalance(exchangeB, valueB);

        assertEquals(Optional.of(valueA), cache.getCachedBalance(exchangeA));
        assertEquals(Optional.of(valueB), cache.getCachedBalance(exchangeB));
    }

    @Test
    public void testCacheExpiration() {
        BigDecimal value = new BigDecimal("123.45");

        cache.setCachedBalance(exchangeA, value, System.currentTimeMillis() - (ExchangeBalanceCache.CACHE_TIMEOUT + 1));

        assertEquals(Optional.empty(), cache.getCachedBalance(exchangeA));
    }

    @Test
    public void testCacheInvalidation() {
        BigDecimal valueA = new BigDecimal("123.45");
        BigDecimal valueB = new BigDecimal("987.65");

        cache.setCachedBalance(exchangeA, valueA);
        cache.setCachedBalance(exchangeB, valueB);

        assertEquals(Optional.of(valueA), cache.getCachedBalance(exchangeA));
        assertEquals(Optional.of(valueB), cache.getCachedBalance(exchangeB));

        cache.invalidate(exchangeA);

        assertEquals(Optional.empty(), cache.getCachedBalance(exchangeA));
        assertEquals(Optional.of(valueB), cache.getCachedBalance(exchangeB));
    }
}
