package com.r307.arbitrader.service.model;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;

import java.math.BigDecimal;

/**
 * Similar to a TradeCombination, but with additional information required to determine whether we should trade
 * or not.
 */
public class Spread {
    private final CurrencyPair currencyPair;
    private final Exchange longExchange;
    private final Exchange shortExchange;
    private final Ticker longTicker;
    private final Ticker shortTicker;
    private final BigDecimal in;
    private final BigDecimal out;

    public Spread(
        CurrencyPair currencyPair,
        Exchange longExchange,
        Exchange shortExchange,
        Ticker longTicker,
        Ticker shortTicker,
        BigDecimal in,
        BigDecimal out) {

        this.currencyPair = currencyPair;
        this.longExchange = longExchange;
        this.shortExchange = shortExchange;
        this.longTicker = longTicker;
        this.shortTicker = shortTicker;
        this.in = in;
        this.out = out;
    }

    public CurrencyPair getCurrencyPair() {
        return currencyPair;
    }

    public Exchange getLongExchange() {
        return longExchange;
    }

    public Exchange getShortExchange() {
        return shortExchange;
    }

    public Ticker getLongTicker() {
        return longTicker;
    }

    public Ticker getShortTicker() {
        return shortTicker;
    }

    public BigDecimal getIn() {
        return in;
    }

    public BigDecimal getOut() {
        return out;
    }

    @Override
    public String toString() {
        return String.format("%s/%s %s %f/%f",
            longExchange.getExchangeSpecification().getExchangeName(),
            shortExchange.getExchangeSpecification().getExchangeName(),
            currencyPair,
            in,
            out);
    }
}
