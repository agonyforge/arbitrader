package com.r307.arbitrader.service.cache;

import org.knowm.xchange.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cache order volumes to avoid rate limiting. Order volumes don't change
 * once they're placed so they're safe to cache.
 */
public class OrderVolumeCache {
    public static final int CACHE_SIZE = 4;

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderVolumeCache.class);

    private final Map<String, BigDecimal> cache = new HashMap<>();
    private final List<String> keys = new ArrayList<>();

    /**
     * Return a cached volume by exchange and order ID.
     *
     * @param exchange The exchange the order is on.
     * @param orderId The order ID of the order.
     * @return The volume of the order, if it is in the cache.
     */
    public Optional<BigDecimal> getCachedVolume(Exchange exchange, String orderId) {
        BigDecimal value = cache.get(computeCacheKey(exchange, orderId));

        if (value == null) {
            LOGGER.debug("Cache returned null for order {}:{}",
                exchange.getExchangeSpecification().getExchangeName(),
                orderId);

            return Optional.empty();
        }

        LOGGER.debug("Cache returned a cached volume for order {}:{}",
            exchange.getExchangeSpecification().getExchangeName(),
            orderId);

        return Optional.of(value);
    }

    /**
     * Put an order volume into the cache. If the cache is larger than CACHE_SIZE
     * after adding the new value, orders will be removed in the order they were added
     * (first in, first out) until the size of the cache equals CACHE_SIZE. This is
     * a feature to avoid unbounded memory growth if Arbitrader is left running
     * for a long period of time. There is no reason at the time of writing this that
     * we would ever want to look up an order volume for a closed order.
     *
     * @param exchange The exchange the order is on.
     * @param orderId The order ID of the order.
     * @param volume The volume of the order.
     */
    public void setCachedVolume(Exchange exchange, String orderId, BigDecimal volume) {
        LOGGER.debug("Caching new value: {}:{} -> {}",
            exchange.getExchangeSpecification().getExchangeName(),
            orderId,
            volume);

        // add the new key to the cache
        cache.put(computeCacheKey(exchange, orderId), volume);
        keys.add(computeCacheKey(exchange, orderId));

        // if the cache is too large, start removing items (FIFO) until it has CACHE_SIZE items
        if (keys.size() > CACHE_SIZE) {
            while (keys.size() > CACHE_SIZE) {
                cache.remove(keys.get(0));
                keys.remove(0);
            }
        }
    }

    // compute a String key suitable for use as the key in a Map
    private String computeCacheKey(Exchange exchange, String orderId) {
        return String.format("%s:%s", exchange.getExchangeSpecification().getExchangeName(), orderId);
    }
}
