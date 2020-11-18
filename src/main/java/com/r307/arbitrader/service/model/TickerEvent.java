package com.r307.arbitrader.service.model;

import org.knowm.xchange.dto.marketdata.Ticker;
import org.springframework.context.ApplicationEvent;

public class TickerEvent extends ApplicationEvent {

    private final Ticker ticker;
    private final boolean isStreamingExchange;

    /**
     * Create a new {@code ApplicationEvent}.
     *
     * @param ticker the object on which the event initially occurred or with
     *               which the event is associated (never {@code null})
     */
    public TickerEvent(Ticker ticker, boolean isStreamingExchange) {
        super(ticker);
        this.ticker = ticker;
        this.isStreamingExchange = isStreamingExchange;
    }

    public Ticker getTicker() {
        return ticker;
    }

    public boolean isStreamingExchange() {
        return isStreamingExchange;
    }
}
