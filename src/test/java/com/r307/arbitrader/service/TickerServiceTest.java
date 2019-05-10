package com.r307.arbitrader.service;

import com.r307.arbitrader.ExchangeBuilder;
import com.r307.arbitrader.config.NotificationConfiguration;
import com.r307.arbitrader.service.ticker.ParallelTickerStrategy;
import com.r307.arbitrader.service.ticker.SingleCallTickerStrategy;
import com.r307.arbitrader.service.ticker.TickerStrategy;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.exceptions.ExchangeException;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TickerServiceTest {
    private List<CurrencyPair> currencyPairs = Collections.singletonList(CurrencyPair.BTC_USD);
    private TickerStrategy singleCallTickerStrategy;
    private TickerStrategy parallelTickerStrategy;

    private TickerService tickerService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        NotificationConfiguration notificationConfiguration = new NotificationConfiguration();
        ExchangeService exchangeService = new ExchangeService();

        singleCallTickerStrategy = new SingleCallTickerStrategy(notificationConfiguration, exchangeService);
        parallelTickerStrategy = new ParallelTickerStrategy(notificationConfiguration, exchangeService);

        tickerService = new TickerService();
    }

    @Test
    public void testGetTickers() throws IOException {
        Exchange exchange = new ExchangeBuilder("CrazyCoinz", CurrencyPair.BTC_USD)
            .withTickerStrategy(singleCallTickerStrategy)
            .withTickers(
                true,
                Collections.singletonList(CurrencyPair.BTC_USD))
            .build();

        List<Ticker> tickers = tickerService.getTickers(exchange, currencyPairs);

        assertFalse(tickers.isEmpty());

        verify(exchange.getMarketDataService()).getTickers(any());
        verify(exchange.getMarketDataService(), never()).getTicker(any());
    }

    @Test
    public void testGetParallelTickers() throws IOException {
        Exchange exchange = new ExchangeBuilder("CrazyCoinz", CurrencyPair.BTC_USD)
            .withTickerStrategy(parallelTickerStrategy)
            .withTickers(
                false,
                Collections.singletonList(CurrencyPair.BTC_USD))
            .build();

        List<Ticker> tickers = tickerService.getTickers(exchange, currencyPairs);

        assertFalse(tickers.isEmpty());

        verify(exchange.getMarketDataService(), never()).getTickers(any());
        verify(exchange.getMarketDataService(), atLeastOnce()).getTicker(any());
    }

    @Test
    public void testGetTickersException() throws IOException {
        Exchange exchange = new ExchangeBuilder("CrazyCoinz", CurrencyPair.BTC_USD)
            .withTickerStrategy(singleCallTickerStrategy)
            .withTickers(new ExchangeException("Boom!"))
            .build();

        List<Ticker> tickers = tickerService.getTickers(exchange, currencyPairs);

        assertTrue(tickers.isEmpty());

        verify(exchange.getMarketDataService()).getTickers(any());
        verify(exchange.getMarketDataService(), never()).getTicker(any());
    }
}
