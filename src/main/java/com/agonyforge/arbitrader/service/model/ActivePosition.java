package com.agonyforge.arbitrader.service.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * All the information we need to store an active pair of trades to disk, and load it back up later.
 * This is used in the state file to enable us to shut Arbitrader down and start it back up again without
 * losing any information.
 */
@JsonIgnoreProperties(ignoreUnknown=true)
public class ActivePosition {
    private final Trade longTrade = new Trade();
    private final Trade shortTrade = new Trade();
    private CurrencyPair currencyPair;
    private BigDecimal exitTarget;
    private BigDecimal entryBalance; // USD balance of both exchanges summed when the trades were first opened
    private OffsetDateTime entryTime;

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

    public BigDecimal getEntryBalance() {
        return entryBalance;
    }

    public void setEntryBalance(BigDecimal entryBalance) {
        this.entryBalance = entryBalance;
    }

    public OffsetDateTime getEntryTime() {
        return entryTime;
    }

    public void setEntryTime(OffsetDateTime entryTime) {
        this.entryTime = entryTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ActivePosition)) return false;
        ActivePosition that = (ActivePosition) o;
        return Objects.equals(getLongTrade(), that.getLongTrade()) &&
            Objects.equals(getShortTrade(), that.getShortTrade()) &&
            Objects.equals(getCurrencyPair(), that.getCurrencyPair()) &&
            Objects.equals(getExitTarget(), that.getExitTarget()) &&
            Objects.equals(getEntryBalance(), that.getEntryBalance()) &&
            Objects.equals(getEntryTime(), that.getEntryTime());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getLongTrade(), getShortTrade(), getCurrencyPair(), getExitTarget(), getEntryBalance(), getEntryTime());
    }

    @Override
    public String toString() {
        return "ActivePosition{" +
            "longTrade=" + longTrade +
            ", shortTrade=" + shortTrade +
            ", currencyPair=" + currencyPair +
            ", exitTarget=" + exitTarget +
            ", entryBalance=" + entryBalance +
            ", entryTime=" + entryTime +
            '}';
    }

    public static class Trade {
        private String exchange;
        private String orderId;
        private BigDecimal volume;
        private BigDecimal entry;

        public String getExchange() {
            return exchange;
        }

        public void setExchange(String exchange) {
            this.exchange = exchange;
        }

        public void setExchange(Exchange exchange) {
            this.exchange = exchange.getExchangeSpecification().getExchangeName();
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Trade)) return false;
            Trade trade = (Trade) o;
            return Objects.equals(getExchange(), trade.getExchange()) &&
                Objects.equals(getOrderId(), trade.getOrderId()) &&
                Objects.equals(getVolume(), trade.getVolume()) &&
                Objects.equals(getEntry(), trade.getEntry());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getExchange(), getOrderId(), getVolume(), getEntry());
        }

        @Override
        public String toString() {
            return "Trade{" +
                "exchange='" + exchange + '\'' +
                ", orderId='" + orderId + '\'' +
                ", volume=" + volume +
                ", entry=" + entry +
                '}';
        }
    }
}
