package com.r307.arbitrader.service;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;

import java.math.BigDecimal;

public class ActivePosition {
    private Trade longTrade = new Trade();
    private Trade shortTrade = new Trade();
    private CurrencyPair currencyPair;
    private BigDecimal exitTarget;

    public Trade getLongTrade() {
        return longTrade;
    }

    public Trade getShortTrade() {
        return shortTrade;
    }

    public CurrencyPair getCurrencyPair() {
        return currencyPair;
    }

    public void setCurrencyPair(CurrencyPair currencyPair) {
        this.currencyPair = currencyPair;
    }

    public BigDecimal getExitTarget() {
        return exitTarget;
    }

    public void setExitTarget(BigDecimal exitTarget) {
        this.exitTarget = exitTarget;
    }

    public static class Trade {
        private Exchange exchange;
        private String orderId;
        private BigDecimal volume;
        private BigDecimal entry;

        public Exchange getExchange() {
            return exchange;
        }

        public void setExchange(Exchange exchange) {
            this.exchange = exchange;
        }

        public String getOrderId() {
            return orderId;
        }

        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }

        public BigDecimal getVolume() {
            return volume;
        }

        public void setVolume(BigDecimal volume) {
            this.volume = volume;
        }

        public BigDecimal getEntry() {
            return entry;
        }

        public void setEntry(BigDecimal entry) {
            this.entry = entry;
        }
    }
}
