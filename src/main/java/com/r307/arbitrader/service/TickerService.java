package com.r307.arbitrader.service;

import com.r307.arbitrader.config.NotificationConfiguration;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.marketdata.params.CurrencyPairsParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import si.mazi.rescu.AwareException;

import javax.inject.Inject;
import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class TickerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TickerService.class);

    private NotificationConfiguration notificationConfiguration;
    private ExchangeService exchangeService;

    @Inject
    public TickerService(NotificationConfiguration notificationConfiguration, ExchangeService exchangeService) {
        this.notificationConfiguration = notificationConfiguration;
        this.exchangeService = exchangeService;
    }

    // TODO refactor me, I'm spaghetti!
    public List<Ticker> getTickers(Exchange exchange, List<CurrencyPair> currencyPairs) {
        MarketDataService marketDataService = exchange.getMarketDataService();

        long start = System.currentTimeMillis();

        try {
            try {
                CurrencyPairsParam param = () -> currencyPairs.stream()
                    .map(currencyPair -> exchangeService.convertExchangePair(exchange, currencyPair))
                    .collect(Collectors.toList());
                List<Ticker> tickers = marketDataService.getTickers(param);

                tickers.forEach(ticker ->
                    LOGGER.debug("{}: {} {}/{}",
                        ticker.getCurrencyPair(),
                        exchange.getExchangeSpecification().getExchangeName(),
                        ticker.getBid(), ticker.getAsk()));

                long completion = System.currentTimeMillis() - start;

                if (completion > notificationConfiguration.getLogs().getSlowTickerWarning()) {
                    LOGGER.warn("Slow Tickers! Fetched {} tickers via getTickers() for {} in {} ms",
                        tickers.size(),
                        exchange.getExchangeSpecification().getExchangeName(),
                        System.currentTimeMillis() - start);
                }

                return tickers;
            } catch (UndeclaredThrowableException ute) {
                // Method proxying in rescu can enclose a real exception in this UTE, so we need to unwrap and re-throw it.
                throw ute.getCause();
            }
        } catch (NotYetImplementedForExchangeException e) {
            LOGGER.debug("{} does not implement MarketDataService.getTickers()", exchange.getExchangeSpecification().getExchangeName());

            List<Ticker> tickers = currencyPairs.parallelStream()
                .map(currencyPair -> {
                    try {
                        try {
                            return marketDataService.getTicker(exchangeService.convertExchangePair(exchange, currencyPair));
                        } catch (UndeclaredThrowableException ute) {
                            // Method proxying in rescu can enclose a real exception in this UTE, so we need to unwrap and re-throw it.
                            throw ute.getCause();
                        }
                    } catch (IOException | NullPointerException | ExchangeException ex) {
                        LOGGER.debug("Unable to fetch ticker for {} {}",
                            exchange.getExchangeSpecification().getExchangeName(),
                            currencyPair);
                    } catch (Throwable t) {
                        // TODO remove this general catch when we stop seeing oddball exceptions

                        LOGGER.warn("Uncaught Throwable class was: {}", t.getClass().getName());

                        if (t instanceof RuntimeException) {
                            throw (RuntimeException) t;
                        } else {
                            LOGGER.error("Not re-throwing checked Exception!");
                        }
                    }

                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            long completion = System.currentTimeMillis() - start;

            if (completion > notificationConfiguration.getLogs().getSlowTickerWarning()) {
                LOGGER.warn("Slow Tickers! Fetched {} tickers via parallelStream for {} getTicker(): {} ms",
                    tickers.size(),
                    exchange.getExchangeSpecification().getExchangeName(),
                    System.currentTimeMillis() - start);
            }

            return tickers;
        } catch (AwareException | ExchangeException | IOException e) {
            LOGGER.debug("Unable to get ticker for {}: {}", exchange.getExchangeSpecification().getExchangeName(), e.getMessage());
        } catch (Throwable t) {
            // TODO remove this general catch when we stop seeing oddball exceptions
            // I hate seeing general catches like this but the method proxying in rescu is throwing some weird ones
            // to us that I'd like to capture and handle appropriately. It's impossible to tell what they actually are
            // without laying a trap like this to catch, inspect and log them at runtime.

            LOGGER.warn("Uncaught Throwable's actual class was: {}", t.getClass().getName());

            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else {
                LOGGER.error("Not re-throwing checked Exception!");
            }
        }

        long completion = System.currentTimeMillis() - start;

        if (completion > notificationConfiguration.getLogs().getSlowTickerWarning()) {
            LOGGER.warn("Slow Tickers! Fetched empty ticker list for {} in {} ms",
                exchange.getExchangeSpecification().getExchangeName(),
                System.currentTimeMillis() - start);
        }

        return Collections.emptyList();
    }
}
