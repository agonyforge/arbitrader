package com.r307.arbitrader;

import info.bitrich.xchangestream.core.StreamingExchange;
import org.knowm.xchange.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public final class Utils {
    private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);

    // Intentionally empty
    private Utils() {}

    public static Class<Exchange> loadExchangeClass(String clazzName) throws ClassNotFoundException {
        Class<?> clazz = Class.forName(clazzName);
        Class<?> superclazz = clazz;

        do {
            superclazz = superclazz.getSuperclass();
        } while (superclazz != null && Arrays.stream(superclazz.getInterfaces()).noneMatch(i -> i.equals(Exchange.class)));

        if (superclazz == null) {
            throw new ClassNotFoundException(String.format("Class %s must extend Exchange", clazzName));
        }

        LOGGER.info("Loaded exchange class: {}", clazz.getCanonicalName());

        //noinspection unchecked
        return (Class<Exchange>)clazz;
    }

    public static boolean isStreamingExchange(Exchange exchange) {
        return exchange instanceof StreamingExchange;
    }
}
