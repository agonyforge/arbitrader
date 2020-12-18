package com.r307.arbitrader.service.model;

import org.knowm.xchange.dto.marketdata.Ticker;
import org.springframework.context.ApplicationEvent;

public class TickerEvent extends ApplicationEvent {

    private final Ticker ticker;
    private final String exchangeName;
    private final boolean isStreamingExchange;

    /**
     * Create a new {@code ApplicationEvent}.
     *
     * @param ticker the object on which the event initially occurred or with
     *               which the event is associated (never {@code null})
     */
    public TickerEvent(Ticker ticker, String exchangeName, boolean isStreamingExchange) {
        super(ticker);
        this.ticker = ticker;
        this.exchangeName = exchangeName;
        this.isStreamingExchange = isStreamingExchange;
    }

    public Ticker getTicker() {
        return ticker;
    }

    public String getExchangeName() {
        return exchangeName;
    }

    public boolean isStreamingExchange() {
        return isStreamingExchange;
    }
}
