package com.r307.arbitrader.service;

import com.r307.arbitrader.service.ticker.TickerStrategy;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;

import static com.r307.arbitrader.service.TradingService.TICKER_STRATEGY_KEY;

@Component
public class TickerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TickerService.class);

    private ErrorCollectorService errorCollectorService;

    @Inject
    public TickerService(ErrorCollectorService errorCollectorService) {
        this.errorCollectorService = errorCollectorService;
    }

    public List<Ticker> getTickers(Exchange exchange, List<CurrencyPair> currencyPairs) {
        TickerStrategy tickerStrategy = (TickerStrategy)exchange.getExchangeSpecification().getExchangeSpecificParametersItem(TICKER_STRATEGY_KEY);

        try {
            return tickerStrategy.getTickers(exchange, currencyPairs);
        } catch (RuntimeException re) {
            LOGGER.debug("Unexpected runtime exception: " + re.getMessage(), re);
            errorCollectorService.collect(exchange, re);
        }

        return Collections.emptyList();
    }
}
