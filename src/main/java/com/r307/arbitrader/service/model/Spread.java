package com.r307.arbitrader.service.model;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;

import java.math.BigDecimal;

public class Spread {
    private CurrencyPair currencyPair;
    private Exchange longExchange;
    private Exchange shortExchange;
    private Ticker longTicker;
    private Ticker shortTicker;
    private BigDecimal in;
    private BigDecimal out;

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
}
