package com.r307.arbitrader.service.ticker;

import com.r307.arbitrader.config.NotificationConfiguration;
import com.r307.arbitrader.service.ErrorCollectorService;
import com.r307.arbitrader.service.ExchangeService;
import org.apache.commons.collections4.ListUtils;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A TickerStrategy that fetches each ticker with its own call to the API, but all in parallel.
 */
@Component
public class ParallelTickerStrategy implements TickerStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParallelTickerStrategy.class);

    private final NotificationConfiguration notificationConfiguration;
    private final ExchangeService exchangeService;
    private final ErrorCollectorService errorCollectorService;

    @Inject
    public ParallelTickerStrategy(
        NotificationConfiguration notificationConfiguration,
        ErrorCollectorService errorCollectorService,
        ExchangeService exchangeService) {

        this.notificationConfiguration = notificationConfiguration;
        this.errorCollectorService = errorCollectorService;
        this.exchangeService = exchangeService;
    }

    @Override
    public List<Ticker> getTickers(Exchange exchange, List<CurrencyPair> currencyPairs) {
        MarketDataService marketDataService = exchange.getMarketDataService();
        Integer tickerBatchDelay = getTickerExchangeDelay(exchange);
        int tickerPartitionSize = getTickerPartitionSize(exchange)
            .getOrDefault("batchSize", Integer.MAX_VALUE);

        long start = System.currentTimeMillis();

        // partition the list of tickers into batches
        List<Ticker> tickers = ListUtils.partition(currencyPairs, tickerPartitionSize)
            .stream()
            .peek(partition -> {
                if (tickerBatchDelay != null) { // delay if we need to, to try and avoid rate limiting
                    try {
                        LOGGER.debug("Sleeping for {} ms...", tickerBatchDelay);
                        Thread.sleep(tickerBatchDelay);
                    } catch (InterruptedException e) {
                        LOGGER.trace("Sleep interrupted");
                    }
                }
            })
            .map(partition ->
                partition
                    // executes the following all in parallel rather than sequentially
                    .parallelStream()
                    .map(currencyPair -> {
                        try {
                            try {
                                // get the ticker
                                Ticker ticker = marketDataService.getTicker(exchangeService.convertExchangePair(exchange, currencyPair));

                                LOGGER.debug("Fetched ticker: {} {} {}/{}",
                                    exchange.getExchangeSpecification().getExchangeName(),
                                    ticker.getInstrument(),
                                    ticker.getBid(),
                                    ticker.getAsk());

                                // and return it
                                return ticker;
                            } catch (UndeclaredThrowableException ute) {
                                // Method proxying in rescu can enclose a real exception in this UTE, so we need to unwrap and re-throw it.
                                throw ute.getCause();
                            }
                        } catch (Throwable t) {
                            errorCollectorService.collect(exchange, t);
                            LOGGER.debug("Unexpected checked exception: " + t.getMessage(), t);
                        }

                        return null;
                    })
                    .filter(Objects::nonNull) // get rid of any nulls we managed to collect
                    .collect(Collectors.toList()) // gather all the tickers we fetched into a list
            )
            .flatMap(List::stream)// turn the lists from all the partitions into a stream
            .collect(Collectors.toList()); // collect them all into a single list

        long completion = System.currentTimeMillis() - start;

        // if all of that took too long, print a warning in the logs
        if (completion > notificationConfiguration.getLogs().getSlowTickerWarning()) {
            LOGGER.warn("Slow Tickers! Fetched {} tickers via parallelStream for {} in {} ms",
                tickers.size(),
                exchange.getExchangeSpecification().getExchangeName(),
                System.currentTimeMillis() - start);
        }

        // return whatever we got
        return tickers;
    }

    // return the batchDelay configuration parameter
    // you can increase this to slow down if you're getting rate limited
    private Integer getTickerExchangeDelay(Exchange exchange) {
        return exchangeService.getExchangeMetadata(exchange).getTicker().get("batchDelay");
    }

    // the size of the partition is based on how many tickers we have for this exchange
    private Map<String, Integer> getTickerPartitionSize(Exchange exchange) {
        return exchangeService.getExchangeMetadata(exchange).getTicker();
    }

    @Override
    public String toString() {
        return "Parallel";
    }
}
