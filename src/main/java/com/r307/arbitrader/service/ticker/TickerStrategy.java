package com.r307.arbitrader.service.ticker;

import com.r307.arbitrader.service.TickerService;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;

import java.util.List;

/**
 * A TickerStrategy defines a way of getting a Ticker from an exchange.
 */
public interface TickerStrategy {
    /**
     * Get a set of Tickers from an Exchange. The TickerStrategy should call
     * putTicker() on the TickerService to ensure that the global ticker map
     * stays up to date. It should also publish a TickerEvent to notify listeners
     * who may be interested in knowing that a new ticker is available.
     *
     * // TODO this method has evolved to do too many things but I'll untangle it later
     *
     * @param exchange The Exchange to get Tickers from.
     * @param currencyPairs The CurrencyPairs to get Tickers for.
     */
    void getTickers(Exchange exchange, List<CurrencyPair> currencyPairs, TickerService tickerService);
}
