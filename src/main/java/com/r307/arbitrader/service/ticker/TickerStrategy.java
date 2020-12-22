package com.r307.arbitrader.service.ticker;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;

import java.util.List;

public interface TickerStrategy {
    void fetchTickers(Exchange exchange, List<CurrencyPair> currencyPairs);
}
