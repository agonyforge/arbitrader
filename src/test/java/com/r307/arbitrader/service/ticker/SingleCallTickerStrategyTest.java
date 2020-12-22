package com.r307.arbitrader.service.ticker;

import com.r307.arbitrader.ExchangeBuilder;
import com.r307.arbitrader.config.NotificationConfiguration;
import com.r307.arbitrader.service.ErrorCollectorService;
import com.r307.arbitrader.service.ExchangeService;
import com.r307.arbitrader.service.event.TickerEventPublisher;
import com.r307.arbitrader.service.model.event.TickerEvent;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.exceptions.ExchangeException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class SingleCallTickerStrategyTest {
    private final List<CurrencyPair> currencyPairs = Collections.singletonList(CurrencyPair.BTC_USD);

    private ErrorCollectorService errorCollectorService;

    private TickerStrategy tickerStrategy;

    private final TickerEventPublisher tickerEventPublisher = mock(TickerEventPublisher.class);

    @Mock
    private ExchangeService exchangeService;

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

        tickerStrategy.fetchTickers(exchange, currencyPairs);
        verify(tickerEventPublisher, times(1)).publishTicker(any(TickerEvent.class));

        assertTrue(errorCollectorService.isEmpty());
    }

    @Test
    public void testGetTickersExchangeException() throws IOException {
        Exchange exchange = new ExchangeBuilder("CrazyCoinz", CurrencyPair.BTC_USD)
            .withTickerStrategy(tickerStrategy)
            .withTickers(new ExchangeException("Boom!"))
            .build();

        verify(tickerEventPublisher, times(0)).publishTicker(any(TickerEvent.class));
        tickerStrategy.fetchTickers(exchange, currencyPairs);

        assertFalse(errorCollectorService.isEmpty());
    }

    @Test
    public void testGetTickersIOException() throws IOException {
        Exchange exchange = new ExchangeBuilder("CrazyCoinz", CurrencyPair.BTC_USD)
            .withTickerStrategy(tickerStrategy)
            .withTickers(new IOException("Boom!"))
            .build();

        tickerStrategy.fetchTickers(exchange, currencyPairs);
        verify(tickerEventPublisher, never()).publishTicker(any(TickerEvent.class));

        assertFalse(errorCollectorService.isEmpty());
    }

    @Test
    public void testGetTickersUndeclaredThrowableException() throws IOException {
        Exchange exchange = new ExchangeBuilder("CrazyCoinz", CurrencyPair.BTC_USD)
            .withTickerStrategy(tickerStrategy)
            .withTickers(new UndeclaredThrowableException(new IOException("Boom!")))
            .build();

        tickerStrategy.fetchTickers(exchange, currencyPairs);
        verify(tickerEventPublisher, never()).publishTicker(any(TickerEvent.class));

        assertFalse(errorCollectorService.isEmpty());
    }
}
