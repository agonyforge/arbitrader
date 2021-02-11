package com.r307.arbitrader;

import info.bitrich.xchangestream.core.StreamingExchange;
import org.knowm.xchange.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * A few utilities to make life easier.
 */
public final class Utils {
    private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);

    // Intentionally empty
    private Utils() {}

    /**
     * Load an exchange class by its name. This used to be handled within XChange but was deprecated.
     *
     * @param clazzName The fully qualified name of the class to load.
     * @return A Class that is an Exchange.
     * @throws ClassNotFoundException when the class cannot be found.
     */
    public static Class<? extends Exchange> loadExchangeClass(String clazzName) throws ClassNotFoundException {
        Class<?> clazz = Class.forName(clazzName); // loads the class into the JVM or throws
        Class<?> superclazz = clazz;

        // walk up each of the superclasses of our class and inspect each of them to see whether any of them implements Exchange
        do {
            superclazz = superclazz.getSuperclass();
        } while (superclazz != null && Arrays.stream(superclazz.getInterfaces()).noneMatch(i -> i.equals(Exchange.class)));

        // if none of the classes implements Exchange, we can't use it
        if (superclazz == null) {
            throw new ClassNotFoundException(String.format("Class %s must extend Exchange", clazzName));
        }

        LOGGER.info("Loaded exchange class: {}", clazz.getCanonicalName());

        return clazz.asSubclass(Exchange.class);
    }

    /**
     * Is this Exchange a StreamingExchange?
     *
     * @param exchange The Exchange to inspect.
     * @return true if the Exchange is also an instance of StreamingExchange
     */
    public static boolean isStreamingExchange(Exchange exchange) {
        return exchange instanceof StreamingExchange;
    }
}
