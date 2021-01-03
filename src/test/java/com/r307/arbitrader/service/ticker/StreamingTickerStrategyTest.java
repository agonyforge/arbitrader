package com.r307.arbitrader.service.ticker;

import com.r307.arbitrader.ExchangeBuilder;
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
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class StreamingTickerStrategyTest {
    @Mock
    private ErrorCollectorService errorCollectorService;

    @Mock
    private ExchangeService exchangeService;

    @Mock
    private TickerService tickerService;

    @Mock
    private TickerEventPublisher tickerEventPublisher;

    private StreamingTickerStrategy streamingTickerStrategy;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        streamingTickerStrategy = new StreamingTickerStrategy(errorCollectorService, exchangeService, tickerEventPublisher);
    }

    @Test
    public void testInvalidExchange() throws Exception {
        Exchange nonStreamingExchange = new ExchangeBuilder("CrazyCoinz",CurrencyPair.BTC_USD).build();

        streamingTickerStrategy.getTickers(
            nonStreamingExchange,
            Collections.singletonList(CurrencyPair.BTC_USD),
            tickerService);

        verify(tickerService, never()).putTicker(eq(nonStreamingExchange), any(Ticker.class));
        verify(tickerEventPublisher, never()).publishTicker(any(TickerEvent.class));
    }
}
