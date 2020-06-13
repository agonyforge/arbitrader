package com.r307.arbitrader.service.ticker;

import com.r307.arbitrader.ExchangeBuilder;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.Assert.assertTrue;

public class StreamingTickerStrategyTest {
    private StreamingTickerStrategy streamingTickerStrategy;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        streamingTickerStrategy = new StreamingTickerStrategy();
    }

    @Test
    public void testInvalidExchange() throws Exception {
        Exchange nonStreamingExchange = new ExchangeBuilder("CrazyCoinz",CurrencyPair.BTC_USD).build();

        List<Ticker> result = streamingTickerStrategy.getTickers(nonStreamingExchange, List.of(CurrencyPair.BTC_USD));

        assertTrue(result.isEmpty());
    }
}
