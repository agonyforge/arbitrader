package com.r307.arbitrader.service;

import org.knowm.xchange.Exchange;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ErrorCollectorService {
    static final String HEADER = "Noncritical error summary: [Exception name]: [Error message] x [Count]";

    private Map<String, Integer> errors = new HashMap<>();

    public void collect(Exchange exchange, Throwable t) {
        errors.compute(computeKey(exchange, t), (key, value) -> (value == null ? 0 : value) + 1);
    }

    public boolean isEmpty() {
        return errors.size() == 0;
    }

    public void clear() {
        errors.clear();
    }

    public List<String> report() {
        List<String> report = new ArrayList<>();

        report.add(HEADER);
        report.addAll(errors.entrySet()
            .stream()
            .map(entry -> entry.getKey() + " x " + entry.getValue())
            .collect(Collectors.toList()));

        return report;
    }

    private String computeKey(Exchange exchange, Throwable t) {
        return exchange.getExchangeSpecification().getExchangeName() + ": " + t.getClass().getSimpleName() + " " + t.getMessage();
    }
}
