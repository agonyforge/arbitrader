package com.r307.arbitrader.service.model;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;

import java.util.Objects;

public class TradeCombination {
    private Exchange longExchange;
    private Exchange shortExchange;
    private CurrencyPair currencyPair;

    public TradeCombination(Exchange longExchange, Exchange shortExchange, CurrencyPair currencyPair) {
        this.longExchange = longExchange;
        this.shortExchange = shortExchange;
        this.currencyPair = currencyPair;
    }

    public Exchange getLongExchange() {
        return longExchange;
    }

    public Exchange getShortExchange() {
        return shortExchange;
    }

    public CurrencyPair getCurrencyPair() {
        return currencyPair;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TradeCombination)) return false;
        TradeCombination that = (TradeCombination) o;
        return Objects.equals(getLongExchange(), that.getLongExchange()) &&
            Objects.equals(getShortExchange(), that.getShortExchange()) &&
            Objects.equals(getCurrencyPair(), that.getCurrencyPair());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getLongExchange(), getShortExchange(), getCurrencyPair());
    }

    @Override
    public String toString() {
        return String.format("%s/%s %s",
            longExchange.getExchangeSpecification().getExchangeName(),
            shortExchange.getExchangeSpecification().getExchangeName(),
            currencyPair.toString());
    }
}
