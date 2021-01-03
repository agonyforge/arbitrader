package com.r307.arbitrader.service.ticker;

import com.r307.arbitrader.service.ErrorCollectorService;
import com.r307.arbitrader.service.ExchangeService;
import com.r307.arbitrader.service.TickerService;
import com.r307.arbitrader.service.event.TickerEventPublisher;
import com.r307.arbitrader.service.model.TickerEvent;
import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingExchange;
import io.reactivex.disposables.Disposable;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A TickerStrategy implementation for streaming exchanges.
 */
public class StreamingTickerStrategy implements TickerStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamingTickerStrategy.class);

    // we would use this list if we supported disconnecting from streams
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private final List<Disposable> subscriptions = new ArrayList<>();
    private final Map<StreamingExchange, Map<CurrencyPair, Ticker>> tickers = new HashMap<>();
    private final ErrorCollectorService errorCollectorService;
    private final ExchangeService exchangeService;
    private final TickerEventPublisher tickerEventPublisher;

    public StreamingTickerStrategy(ErrorCollectorService errorCollectorService,
                                   ExchangeService exchangeService,
                                   TickerEventPublisher tickerEventPublisher) {
        this.errorCollectorService = errorCollectorService;
        this.exchangeService = exchangeService;
        this.tickerEventPublisher = tickerEventPublisher;
    }

    @Override
    public void getTickers(Exchange stdExchange, List<CurrencyPair> currencyPairs, TickerService tickerService) {
        if (!(stdExchange instanceof StreamingExchange)) {
            LOGGER.warn("{} is not a streaming exchange", stdExchange.getExchangeSpecification().getExchangeName());
            return;
        }

        StreamingExchange exchange = (StreamingExchange)stdExchange;

        // TODO could the following "if" be the reason for the spurious reconnects?
        if (!tickers.containsKey(exchange)) { // we're not receiving prices so we need to (re)connect
            ProductSubscription.ProductSubscriptionBuilder builder = ProductSubscription.create();

            currencyPairs.forEach(builder::addTicker);

            // try to subscribe to the websocket
            exchange.connect(builder.build()).blockingAwait();
            subscriptions.clear(); // avoid endlessly filling this list up with dead subscriptions
            subscriptions.addAll(subscribeAll(exchange, currencyPairs, tickerService));
        }
    }

    // listen to websocket messages, populate the ticker map and publish ticker events
    private List<Disposable> subscribeAll(StreamingExchange exchange, List<CurrencyPair> currencyPairs, TickerService tickerService) {
        return currencyPairs
            .stream()
            .map(pair -> {
                final CurrencyPair currencyPair = exchangeService.convertExchangePair(exchange, pair);
                final List<Object> tickerArguments = exchangeService.getExchangeMetadata(exchange).getTickerArguments();

                return exchange.getStreamingMarketDataService().getTicker(currencyPair, tickerArguments.toArray())
                    .doOnNext(ticker -> log(exchange, ticker))
                    .subscribe(
                        ticker -> {
                            tickers.computeIfAbsent(exchange, e -> new HashMap<>());

                            // don't waste time analyzing duplicate tickers
                            Ticker oldTicker = tickers.get(exchange).get(pair);

                            if (oldTicker != null
                                && oldTicker.getInstrument().equals(ticker.getInstrument())
                                && oldTicker.getBid().equals(ticker.getBid())
                                && oldTicker.getAsk().equals(ticker.getAsk())) {
                                return;
                            }

                            // store the ticker in our cache
                            tickers.get(exchange).put(pair, ticker);

                            // store the ticker in the TickerService
                            tickerService.putTicker(exchange, ticker);

                            // publish an event to notify that the tickers have updated
                            tickerEventPublisher.publishTicker(new TickerEvent(ticker, exchange));
                        },
                        throwable -> {
                            // collect errors quietly, but expose them in the debug log
                            errorCollectorService.collect(exchange, throwable);
                            LOGGER.debug("Unexpected checked exception: {}", throwable.getMessage(), throwable);
                    });
            })
            .collect(Collectors.toList());
    }

    // debug logging whenever we get a ticker event
    private void log(StreamingExchange exchange, Ticker ticker) {
        LOGGER.debug("Received ticker: {} {} {}/{}",
            exchange.getExchangeSpecification().getExchangeName(),
            ticker.getInstrument(),
            ticker.getBid(),
            ticker.getAsk());
    }

    @Override
    public String toString() {
        return "Streaming";
    }
}
