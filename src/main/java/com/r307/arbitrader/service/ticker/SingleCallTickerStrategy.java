package com.r307.arbitrader.service.ticker;

import com.r307.arbitrader.config.NotificationConfiguration;
import com.r307.arbitrader.service.ErrorCollectorService;
import com.r307.arbitrader.service.ExchangeService;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.marketdata.params.CurrencyPairsParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A TickerStrategy implementation that makes one call to get multiple Tickers.
 */
@Component
public class SingleCallTickerStrategy implements TickerStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(SingleCallTickerStrategy.class);

    private final NotificationConfiguration notificationConfiguration;
    private final ExchangeService exchangeService;
    private final ErrorCollectorService errorCollectorService;

    @Inject
    public SingleCallTickerStrategy(
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

        long start = System.currentTimeMillis();

        try {
            try {
                CurrencyPairsParam param = () -> currencyPairs.stream()
                    .map(currencyPair -> exchangeService.convertExchangePair(exchange, currencyPair))
                    .collect(Collectors.toList());

                // call the service with all our CurrencyPairs as the parameter
                List<Ticker> tickers = marketDataService.getTickers(param);

                tickers.forEach(ticker -> LOGGER.debug("Fetched ticker: {} {} {}/{}",
                    exchange.getExchangeSpecification().getExchangeName(),
                    ticker.getInstrument(),
                    ticker.getBid(),
                    ticker.getAsk()));

                long completion = System.currentTimeMillis() - start;

                // if it's too slow, put a warning in the logs
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
        } catch (Throwable t) {
            // collect any errors and show them in a summarized way
            errorCollectorService.collect(exchange, t);
            LOGGER.debug("Unexpected checked exception: " + t.getMessage(), t);
        }

        // hopefully we returned some tickers above, but just in case return an empty list so things don't crash
        return Collections.emptyList();
    }

    @Override
    public String toString() {
        return "Single Call";
    }
}
