package com.agonyforge.arbitrader.service;

/**
 * Thrown when an exchange has too little liquidity for us to place the order we want.
 */
public class LiquidityException extends RuntimeException {
    public LiquidityException(String message) {
        super(message);
    }
}
