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

import static com.r307.arbitrader.service.cache.OrderVolumeCache.CACHE_SIZE;
import static org.junit.Assert.assertEquals;

public class OrderVolumeCacheTest extends BaseTestCase {
    private Exchange exchangeA;
    private Exchange exchangeB;

    private OrderVolumeCache cache;

    @Before
    public void setUp() throws IOException {
        exchangeA = new ExchangeBuilder("CoinDynasty", CurrencyPair.BTC_USD).build();
        exchangeB = new ExchangeBuilder("CoinSnake", CurrencyPair.BTC_USD).build();

        cache = new OrderVolumeCache();
    }

    @Test
    public void testGetCachedVolume() {
        BigDecimal valueA = new BigDecimal("123.45");
        BigDecimal valueB = new BigDecimal("987.65");

        cache.setCachedVolume(exchangeA, "1", valueA);
        cache.setCachedVolume(exchangeB, "2", valueB);

        assertEquals(Optional.of(valueA), cache.getCachedVolume(exchangeA, "1"));
        assertEquals(Optional.empty(), cache.getCachedVolume(exchangeA, "2"));
        assertEquals(Optional.of(valueB), cache.getCachedVolume(exchangeB, "2"));
        assertEquals(Optional.empty(), cache.getCachedVolume(exchangeB, "1"));
    }

    @Test
    public void testGetCachedVolumeSameIds() {
        BigDecimal valueA = new BigDecimal("123.45");
        BigDecimal valueB = new BigDecimal("987.65");

        cache.setCachedVolume(exchangeA, "1", valueA);
        cache.setCachedVolume(exchangeB, "1", valueB);

        assertEquals(Optional.of(valueA), cache.getCachedVolume(exchangeA, "1"));
        assertEquals(Optional.empty(), cache.getCachedVolume(exchangeA, "2"));
        assertEquals(Optional.of(valueB), cache.getCachedVolume(exchangeB, "1"));
        assertEquals(Optional.empty(), cache.getCachedVolume(exchangeB, "2"));
    }

    @Test
    public void testCacheSize() {
        for (int i = 0; i < CACHE_SIZE; i++) {
            BigDecimal value = new BigDecimal(i + ".00");
            String orderId = "Order" + i;

            cache.setCachedVolume(exchangeA, orderId, value);
        }

        for (int i = 0; i < CACHE_SIZE; i++) {
            assertEquals(
                Optional.of(new BigDecimal(i + ".00")),
                cache.getCachedVolume(exchangeA, "Order" + i));
        }

        BigDecimal lastValue = new BigDecimal(CACHE_SIZE + ".00");
        String lastOrderId = "Order" + CACHE_SIZE;

        cache.setCachedVolume(exchangeA, lastOrderId, lastValue);

        assertEquals(Optional.of(lastValue), cache.getCachedVolume(exchangeA, lastOrderId));
        assertEquals(Optional.empty(), cache.getCachedVolume(exchangeA, "Order0"));
    }
}
