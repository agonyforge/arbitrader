package com.r307.arbitrader.service;

import org.knowm.xchange.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Collect non-critical errors and report them together as a batch or summary.
 * This reduces unimportant things in the logs and saves from rate limiting when sending logs to other services.
 */
@Component
public class ErrorCollectorService {
    static final String HEADER = "Noncritical error summary: [Exception name]: [Error message] x [Count]";

    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorCollectorService.class);

    private final Map<String, Integer> errors = new HashMap<>();

    /**
     * Collect an error and store it.
     *
     * @param exchange The Exchange this error is related to.
     * @param t The error object.
     */
    public void collect(Exchange exchange, Throwable t) {
        // store the error in the map and increment the count if there's already a similar error
        errors.compute(computeKey(exchange, t), (key, value) -> (value == null ? 0 : value) + 1);

        // when DEBUG is enabled, show the exception to help with debugging problems
        LOGGER.debug("Surfacing noncritical stack trace for debugging: ", t);
    }

    /**
     * Tells whether the error collector is empty.
     *
     * @return true if the error collector is empty.
     */
    public boolean isEmpty() {
        return errors.size() == 0;
    }

    /**
     * Clear any errors stored in the error collector.
     */
    public void clear() {
        errors.clear();
    }

    /**
     * Generate a report of any errors stored in the error collector.
     *
     * @return a report of stored errors, formatted as a list of strings
     */
    public List<String> report() {
        List<String> report = new ArrayList<>();

        report.add(HEADER);
        report.addAll(errors.entrySet()
            .stream()
            .map(entry -> entry.getKey() + " x " + entry.getValue())
            .collect(Collectors.toList()));

        return report;
    }

    // compute a string based on an Exchange and a Throwable, suitable for use as a key in a Map
    private String computeKey(Exchange exchange, Throwable t) {
        return exchange.getExchangeSpecification().getExchangeName() + ": " + t.getClass().getSimpleName() + " " + t.getMessage();
    }
}
