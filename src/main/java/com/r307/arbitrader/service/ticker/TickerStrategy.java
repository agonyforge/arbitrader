package com.r307.arbitrader.service.ticker;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;

import java.util.List;

/**
 * A TickerStrategy defines a way of getting a Ticker from an exchange.
 */
public interface TickerStrategy {
    /**
     * Fetch a list of Tickers from an Exchange.
     *
     * @param exchange The Exchange to get Tickers from.
     * @param currencyPairs The CurrencyPairs to get Tickers for.
     */
    void fetchTickers(Exchange exchange, List<CurrencyPair> currencyPairs);
}
