package com.r307.arbitrader.service.model;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.springframework.context.ApplicationEvent;

/**
 * An event generated when we receive a ticker from an exchange.
 */
public class TickerEvent extends ApplicationEvent {

    private final Ticker ticker;
    private final Exchange exchange;

    /**
     * Create a new {@code ApplicationEvent}.
     *
     * @param ticker the object on which the event initially occurred or with
     *               which the event is associated (never {@code null})
     */
    public TickerEvent(Ticker ticker, Exchange exchange) {
        super(ticker);
        this.ticker = ticker;
        this.exchange = exchange;
    }

    public Ticker getTicker() {
        return ticker;
    }

    public Exchange getExchange() {
        return exchange;
    }
}
