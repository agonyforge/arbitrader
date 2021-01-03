package com.r307.arbitrader.service.ticker;

import com.r307.arbitrader.ExchangeBuilder;
import com.r307.arbitrader.config.NotificationConfiguration;
import com.r307.arbitrader.service.ErrorCollectorService;
import com.r307.arbitrader.service.ExchangeService;
import com.r307.arbitrader.service.TickerService;
import com.r307.arbitrader.service.event.TickerEventPublisher;
import com.r307.arbitrader.service.model.TickerEvent;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.exceptions.ExchangeException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class SingleCallTickerStrategyTest {
    private List<CurrencyPair> currencyPairs = Collections.singletonList(CurrencyPair.BTC_USD);

    private ErrorCollectorService errorCollectorService;
    private TickerStrategy tickerStrategy;

    @Mock
    private ExchangeService exchangeService;

    @Mock
    private TickerService tickerService;

    @Mock
    private TickerEventPublisher tickerEventPublisher;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        NotificationConfiguration notificationConfiguration = new NotificationConfiguration();

        errorCollectorService = new ErrorCollectorService();

        tickerStrategy = new SingleCallTickerStrategy(notificationConfiguration, errorCollectorService, exchangeService, tickerEventPublisher);
    }

    @Test
    public void testGetTickers() throws IOException {
        Exchange exchange = new ExchangeBuilder("CrazyCoinz", CurrencyPair.BTC_USD)
            .withTickerStrategy(tickerStrategy)
            .withTickers(
                true,
                Collections.singletonList(CurrencyPair.BTC_USD))
            .build();

        tickerStrategy.getTickers(exchange, currencyPairs, tickerService);

        assertTrue(errorCollectorService.isEmpty());

        verify(tickerService).putTicker(eq(exchange), any(Ticker.class));
        verify(tickerEventPublisher).publishTicker(any(TickerEvent.class));
    }

    @Test
    public void testGetTickersExchangeException() throws IOException {
        Exchange exchange = new ExchangeBuilder("CrazyCoinz", CurrencyPair.BTC_USD)
            .withTickerStrategy(tickerStrategy)
            .withTickers(new ExchangeException("Boom!"))
            .build();

        tickerStrategy.getTickers(exchange, currencyPairs, tickerService);

        assertFalse(errorCollectorService.isEmpty());

        verify(tickerService, never()).putTicker(eq(exchange), any(Ticker.class));
        verify(tickerEventPublisher, never()).publishTicker(any(TickerEvent.class));
    }

    @Test
    public void testGetTickersIOException() throws IOException {
        Exchange exchange = new ExchangeBuilder("CrazyCoinz", CurrencyPair.BTC_USD)
            .withTickerStrategy(tickerStrategy)
            .withTickers(new IOException("Boom!"))
            .build();

        tickerStrategy.getTickers(exchange, currencyPairs, tickerService);

        assertFalse(errorCollectorService.isEmpty());

        verify(tickerService, never()).putTicker(eq(exchange), any(Ticker.class));
        verify(tickerEventPublisher, never()).publishTicker(any(TickerEvent.class));
    }

    @Test
    public void testGetTickersUndeclaredThrowableException() throws IOException {
        Exchange exchange = new ExchangeBuilder("CrazyCoinz", CurrencyPair.BTC_USD)
            .withTickerStrategy(tickerStrategy)
            .withTickers(new UndeclaredThrowableException(new IOException("Boom!")))
            .build();

        tickerStrategy.getTickers(exchange, currencyPairs, tickerService);

        assertFalse(errorCollectorService.isEmpty());

        verify(tickerService, never()).putTicker(eq(exchange), any(Ticker.class));
        verify(tickerEventPublisher, never()).publishTicker(any(TickerEvent.class));
    }
}
