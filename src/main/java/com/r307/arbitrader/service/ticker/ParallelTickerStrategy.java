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
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class ParallelTickerStrategy implements TickerStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParallelTickerStrategy.class);

    private NotificationConfiguration notificationConfiguration;
    private ExchangeService exchangeService;
    private ErrorCollectorService errorCollectorService;

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
        Integer tickerBatchDelay = exchangeService.getExchangeMetadata(exchange).getTicker().get("batchDelay");
        int tickerPartitionSize = exchangeService.getExchangeMetadata(exchange).getTicker()
            .getOrDefault("batchSize", Integer.MAX_VALUE);

        long start = System.currentTimeMillis();

        List<Ticker> tickers = ListUtils.partition(currencyPairs, tickerPartitionSize)
            .stream()
            .peek(partition -> {
                if (tickerBatchDelay != null) {
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
                    .parallelStream()
                    .map(currencyPair -> {
                        try {
                            try {
                                Ticker ticker = marketDataService.getTicker(exchangeService.convertExchangePair(exchange, currencyPair));

                                LOGGER.debug("Fetched ticker: {} {} {}/{}",
                                    exchange.getExchangeSpecification().getExchangeName(),
                                    ticker.getCurrencyPair(),
                                    ticker.getBid(),
                                    ticker.getAsk());

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
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList())
            )
            .flatMap(List::stream)
            .collect(Collectors.toList());

        long completion = System.currentTimeMillis() - start;

        if (completion > notificationConfiguration.getLogs().getSlowTickerWarning()) {
            LOGGER.warn("Slow Tickers! Fetched {} tickers via parallelStream for {} in {} ms",
                tickers.size(),
                exchange.getExchangeSpecification().getExchangeName(),
                System.currentTimeMillis() - start);
        }

        return tickers;
    }
}
