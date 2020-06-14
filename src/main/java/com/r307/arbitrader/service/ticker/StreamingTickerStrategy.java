package com.r307.arbitrader.service.ticker;

import com.r307.arbitrader.service.ErrorCollectorService;
import info.bitrich.xchangestream.core.StreamingExchange;
import io.reactivex.disposables.Disposable;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class StreamingTickerStrategy implements TickerStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamingTickerStrategy.class);

    // TODO not sure if this needs reconnect logic or if it is handled internally - needs testing
    private final List<Disposable> subscriptions = new ArrayList<>();
    private final Map<StreamingExchange, Map<CurrencyPair, Ticker>> tickers = new HashMap<>();
    private final ErrorCollectorService errorCollectorService;

    @Inject
    public StreamingTickerStrategy(ErrorCollectorService errorCollectorService) {
        this.errorCollectorService = errorCollectorService;
    }

    @Override
    public List<Ticker> getTickers(Exchange stdExchange, List<CurrencyPair> currencyPairs) {
        if (!(stdExchange instanceof StreamingExchange)) {
            LOGGER.warn("{} is not a streaming exchange", stdExchange.getExchangeSpecification().getExchangeName());
            return Collections.emptyList();
        }

        StreamingExchange exchange = (StreamingExchange)stdExchange;

        if (!tickers.containsKey(exchange)) {
            exchange.connect().blockingAwait();
            subscriptions.addAll(subscribeAll(exchange, currencyPairs));
        }

        if (tickers.containsKey(exchange)) {
            return tickers.get(exchange).entrySet()
                .stream()
                .filter(entry -> currencyPairs.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    private List<Disposable> subscribeAll(StreamingExchange exchange, List<CurrencyPair> currencyPairs) {
        return currencyPairs
            .stream()
            .map(pair -> exchange.getStreamingMarketDataService().getTicker(pair).subscribe(
                ticker -> {
                    tickers.computeIfAbsent(exchange, e -> new HashMap<>());
                    tickers.get(exchange).put(pair, ticker);

                    LOGGER.debug("Received ticker: {} {} {}/{}",
                        exchange.getExchangeSpecification().getExchangeName(),
                        ticker.getCurrencyPair(),
                        ticker.getBid(),
                        ticker.getAsk());
                },
                throwable -> {
                    errorCollectorService.collect(exchange, throwable);
                    LOGGER.debug("Unexpected checked exception: " + throwable.getMessage(), throwable);
                }))
            .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return "Streaming";
    }
}
