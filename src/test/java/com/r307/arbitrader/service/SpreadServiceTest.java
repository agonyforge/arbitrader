package com.r307.arbitrader.service;

import com.r307.arbitrader.BaseTestCase;
import com.r307.arbitrader.ExchangeBuilder;
import com.r307.arbitrader.config.TradingConfiguration;
import com.r307.arbitrader.service.model.Spread;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.mockito.Mock;

import java.io.IOException;
import java.math.BigDecimal;

public class SpreadServiceTest extends BaseTestCase {
    private Exchange longExchange;
    private Exchange shortExchange;

    @Mock
    private TradingConfiguration tradingConfiguration;

    @Mock
    private TickerService tickerService;

    private SpreadService spreadService;

    @Before
    public void setUp() throws IOException {
        longExchange = new ExchangeBuilder("Long", CurrencyPair.BTC_USD)
            .withExchangeMetaData()
            .build();
        shortExchange = new ExchangeBuilder("Short", CurrencyPair.BTC_USD)
            .withExchangeMetaData()
            .build();

        spreadService = new SpreadService(tradingConfiguration, tickerService);
    }

    @Test
    public void testSummary() {
        Spread spread = new Spread(
            CurrencyPair.BTC_USD,
            longExchange,
            shortExchange,
            null,
            null,
            BigDecimal.valueOf(-0.005),
            BigDecimal.valueOf(0.005));

        spreadService.publish(spread);
        spreadService.summary();
    }
}
