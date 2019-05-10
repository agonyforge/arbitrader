package com.r307.arbitrader.service.ticker;

import com.r307.arbitrader.ExchangeBuilder;
import com.r307.arbitrader.config.NotificationConfiguration;
import com.r307.arbitrader.service.ExchangeService;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.exceptions.ExchangeException;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class SingleCallTickerStrategyTest {
    private List<CurrencyPair> currencyPairs = Collections.singletonList(CurrencyPair.BTC_USD);

    private TickerStrategy tickerStrategy;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        NotificationConfiguration notificationConfiguration = new NotificationConfiguration();
        ExchangeService exchangeService = new ExchangeService();

        tickerStrategy = new SingleCallTickerStrategy(notificationConfiguration, exchangeService);
    }

    @Test
    public void testGetTickers() throws IOException {
        Exchange exchange = new ExchangeBuilder("CrazyCoinz", CurrencyPair.BTC_USD)
            .withTickerStrategy(tickerStrategy)
            .withTickers(
                true,
                Collections.singletonList(CurrencyPair.BTC_USD))
            .build();

        List<Ticker> tickers = tickerStrategy.getTickers(exchange, currencyPairs);

        assertEquals(1, tickers.size());

        Ticker ticker = tickers.get(0);

        assertEquals(CurrencyPair.BTC_USD, ticker.getCurrencyPair());
    }

    @Test(expected = ExchangeException.class)
    public void testGetTickersExchangeException() throws IOException {
        Exchange exchange = new ExchangeBuilder("CrazyCoinz", CurrencyPair.BTC_USD)
            .withTickerStrategy(tickerStrategy)
            .withTickers(new ExchangeException("Boom!"))
            .build();

        tickerStrategy.getTickers(exchange, currencyPairs);
    }

    @Test
    public void testGetTickersIOException() throws IOException {
        Exchange exchange = new ExchangeBuilder("CrazyCoinz", CurrencyPair.BTC_USD)
            .withTickerStrategy(tickerStrategy)
            .withTickers(new IOException("Boom!"))
            .build();

        List<Ticker> tickers = tickerStrategy.getTickers(exchange, currencyPairs);

        assertTrue(tickers.isEmpty());
    }

    @Test
    public void testGetTickersUndeclaredThrowableException() throws IOException {
        Exchange exchange = new ExchangeBuilder("CrazyCoinz", CurrencyPair.BTC_USD)
            .withTickerStrategy(tickerStrategy)
            .withTickers(new UndeclaredThrowableException(new IOException("Boom!")))
            .build();

        List<Ticker> tickers = tickerStrategy.getTickers(exchange, currencyPairs);

        assertTrue(tickers.isEmpty());
    }
}
