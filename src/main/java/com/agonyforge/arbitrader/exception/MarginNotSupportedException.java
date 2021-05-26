package com.agonyforge.arbitrader.exception;

public class MarginNotSupportedException extends RuntimeException {
    public MarginNotSupportedException(String exchangeName) {
        super("Margin is not enabled for exchange: " + exchangeName);
    }
}
