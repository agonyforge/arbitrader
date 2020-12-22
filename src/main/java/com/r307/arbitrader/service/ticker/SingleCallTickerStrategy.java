package com.r307.arbitrader.service.ticker;

import com.r307.arbitrader.config.NotificationConfiguration;
import com.r307.arbitrader.service.ErrorCollectorService;
import com.r307.arbitrader.service.ExchangeService;
import com.r307.arbitrader.service.event.TickerEventPublisher;
import com.r307.arbitrader.service.model.event.TickerEvent;
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
import java.util.List;
import java.util.stream.Collectors;

@Component
public class SingleCallTickerStrategy implements TickerStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(SingleCallTickerStrategy.class);

    private final NotificationConfiguration notificationConfiguration;
    private final ExchangeService exchangeService;
    private final ErrorCollectorService errorCollectorService;
    private final TickerEventPublisher tickerEventPublisher;

    @Inject
    public SingleCallTickerStrategy(
        NotificationConfiguration notificationConfiguration,
        ErrorCollectorService errorCollectorService,
        ExchangeService exchangeService,
        TickerEventPublisher tickerEventPublisher) {

        this.notificationConfiguration = notificationConfiguration;
        this.errorCollectorService = errorCollectorService;
        this.exchangeService = exchangeService;
        this.tickerEventPublisher = tickerEventPublisher;
    }

    @Override
    public void fetchTickers(Exchange exchange, List<CurrencyPair> currencyPairs) {
        MarketDataService marketDataService = exchange.getMarketDataService();

        long start = System.currentTimeMillis();

        try {
            try {
                CurrencyPairsParam param = () -> currencyPairs.stream()
                    .map(currencyPair -> exchangeService.convertExchangePair(exchange, currencyPair))
                    .collect(Collectors.toList());

                List<Ticker> tickers = marketDataService.getTickers(param);

                tickers.forEach(ticker -> {
                    LOGGER.debug("Fetched ticker: {} {} {}/{}",
                        exchange.getExchangeSpecification().getExchangeName(),
                        ticker.getInstrument(),
                        ticker.getBid(),
                        ticker.getAsk());

                    tickerEventPublisher.publishTicker(new TickerEvent(ticker, exchange));
                });

                long completion = System.currentTimeMillis() - start;

                if (completion > notificationConfiguration.getLogs().getSlowTickerWarning()) {
                    LOGGER.warn("Slow Tickers! Fetched {} tickers via getTickers() for {} in {} ms",
                        tickers.size(),
                        exchange.getExchangeSpecification().getExchangeName(),
                        System.currentTimeMillis() - start);
                }
            } catch (UndeclaredThrowableException ute) {
                // Method proxying in rescu can enclose a real exception in this UTE, so we need to unwrap and re-throw it.
                throw ute.getCause();
            }
        } catch (Throwable t) {
            errorCollectorService.collect(exchange, t);
            LOGGER.debug("Unexpected checked exception: " + t.getMessage(), t);
        }
    }

    @Override
    public String toString() {
        return "Single Call";
    }
}
