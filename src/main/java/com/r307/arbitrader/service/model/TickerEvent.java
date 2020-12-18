package com.r307.arbitrader.service.model;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.springframework.context.ApplicationEvent;

public class TickerEvent extends ApplicationEvent {

    private final Ticker ticker;
    private final Exchange exchange;
    private final boolean isStreamingExchange;

    /**
     * Create a new {@code ApplicationEvent}.
     *
     * @param ticker the object on which the event initially occurred or with
     *               which the event is associated (never {@code null}).
     * @param exchange the exchange from where this ticker originated.
     * @param isStreamingExchange true if the exchange is a streaming exchange. False otherwise.
     */
    public TickerEvent(Ticker ticker, Exchange exchange, boolean isStreamingExchange) {
        super(ticker);
        this.ticker = ticker;
        this.exchange = exchange;
        this.isStreamingExchange = isStreamingExchange;
    }

    public Ticker getTicker() {
        return ticker;
    }

    public Exchange getExchange() {
        return exchange;
    }

    public boolean isStreamingExchange() {
        return isStreamingExchange;
    }
}
