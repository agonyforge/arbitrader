package com.r307.arbitrader.service.ticker;

import com.r307.arbitrader.service.ErrorCollectorService;
import com.r307.arbitrader.service.ExchangeService;
import com.r307.arbitrader.service.event.TickerEventPublisher;
import com.r307.arbitrader.service.model.event.TickerEvent;
import info.bitrich.xchangestream.core.ProductSubscription;
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

    // we would use this list if we supported disconnecting from streams
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private final List<Disposable> subscriptions = new ArrayList<>();
//    private final Map<StreamingExchange, Map<CurrencyPair, Ticker>> tickers = new HashMap<>();
    private final ErrorCollectorService errorCollectorService;
    private final ExchangeService exchangeService;
    private final TickerEventPublisher tickerEventPublisher;

    @Inject
    public StreamingTickerStrategy(ErrorCollectorService errorCollectorService, ExchangeService exchangeService,
                                   TickerEventPublisher tickerEventPublisher) {
        this.errorCollectorService = errorCollectorService;
        this.exchangeService = exchangeService;
        this.tickerEventPublisher = tickerEventPublisher;
    }

    @Override
    public void fetchTickers(Exchange stdExchange, List<CurrencyPair> currencyPairs) {
        if (!(stdExchange instanceof StreamingExchange)) {
            LOGGER.warn("{} is not a streaming exchange", stdExchange.getExchangeSpecification().getExchangeName());
            return;
        }

        StreamingExchange exchange = (StreamingExchange) stdExchange;

        // If subscriptions is not empty then it means we have already subscribed to the streaming tickets
        if (!subscriptions.isEmpty()) {
            return;
        }

        ProductSubscription.ProductSubscriptionBuilder builder = ProductSubscription.create();

        currencyPairs.forEach(builder::addTicker);

        exchange.connect(builder.build()).blockingAwait();
        subscriptions.clear(); // avoid endlessly filling this list up with dead subscriptions
        subscriptions.addAll(subscribeAll(exchange, currencyPairs));
    }

    private List<Disposable> subscribeAll(StreamingExchange exchange, List<CurrencyPair> currencyPairs) {
        return currencyPairs
            .stream()
            .map(pair -> {
                final CurrencyPair currencyPair = exchangeService.convertExchangePair(exchange, pair);
                final List<Object> tickerArguments = exchangeService.getExchangeMetadata(exchange).getTickerArguments();

                return exchange.getStreamingMarketDataService().getTicker(currencyPair, tickerArguments.toArray())
                    .doOnNext(ticker -> log(exchange, ticker))
                    .subscribe(
                        ticker -> tickerEventPublisher.publishTicker(new TickerEvent(ticker, exchange)),
                        throwable -> {
                            errorCollectorService.collect(exchange, throwable);
                            LOGGER.debug("Unexpected checked exception: {}", throwable.getMessage(), throwable);
                    });
            })
            .collect(Collectors.toList());
    }

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
