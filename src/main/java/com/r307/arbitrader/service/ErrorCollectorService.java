package com.r307.arbitrader.service;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ErrorCollectorService {
    private Map<String, Integer> errors = new HashMap<>();

    public void collect(Throwable t) {
        errors.compute(computeKey(t), (key, value) -> (value == null ? 0 : value) + 1);
    }

    public boolean isEmpty() {
        return errors.size() == 0;
    }

    public void clear() {
        errors.clear();
    }

    public String report() {
        return errors.entrySet()
            .stream()
            .map(entry -> entry.getKey() + " x " + entry.getValue())
            .collect(Collectors.joining("\n"));
    }

    private String computeKey(Throwable t) {
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }
}
