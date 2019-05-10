package com.r307.arbitrader.service.ticker;

import com.r307.arbitrader.config.NotificationConfiguration;
import com.r307.arbitrader.service.ExchangeService;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class ParallelTickerStrategy implements TickerStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParallelTickerStrategy.class);

    private NotificationConfiguration notificationConfiguration;
    private ExchangeService exchangeService;

    @Inject
    public ParallelTickerStrategy(NotificationConfiguration notificationConfiguration, ExchangeService exchangeService) {
        this.notificationConfiguration = notificationConfiguration;
        this.exchangeService = exchangeService;
    }

    @Override
    public List<Ticker> getTickers(Exchange exchange, List<CurrencyPair> currencyPairs) {
        MarketDataService marketDataService = exchange.getMarketDataService();

        long start = System.currentTimeMillis();

        List<Ticker> tickers = currencyPairs.parallelStream()
            .map(currencyPair -> {
                try {
                    try {
                        return marketDataService.getTicker(exchangeService.convertExchangePair(exchange, currencyPair));
                    } catch (UndeclaredThrowableException ute) {
                        // Method proxying in rescu can enclose a real exception in this UTE, so we need to unwrap and re-throw it.
                        throw ute.getCause();
                    }
                } catch (Throwable t) {
                    if (t instanceof RuntimeException) {
                        throw (RuntimeException) t;
                    }

                    LOGGER.warn("Unexpected checked exception: " + t.getMessage(), t);
                }

                return null;
            })
            .filter(Objects::nonNull)
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
