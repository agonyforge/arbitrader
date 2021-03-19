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

import static org.junit.Assert.assertEquals;

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
    public void testRecordLowSpreadIn() {
        Spread spreadBaseline = new Spread(
            CurrencyPair.BTC_USD,
            longExchange,
            shortExchange,
            null,
            null,
            BigDecimal.valueOf(-0.0050),
            BigDecimal.valueOf(0.0050));
        Spread spreadRecordHighIn = new Spread(
            CurrencyPair.BTC_USD,
            longExchange,
            shortExchange,
            null,
            null,
            BigDecimal.valueOf(-0.0051),
            BigDecimal.valueOf(0.0050));

        spreadService.publish(spreadBaseline);

        assertEquals(BigDecimal.valueOf(-0.0050), spreadService.getSpreadRecord(longExchange, shortExchange, CurrencyPair.BTC_USD, "maxSpreadIn"));
        assertEquals(BigDecimal.valueOf(-0.0050), spreadService.getSpreadRecord(longExchange, shortExchange, CurrencyPair.BTC_USD, "minSpreadIn"));
        assertEquals(BigDecimal.valueOf(0.0050), spreadService.getSpreadRecord(longExchange, shortExchange, CurrencyPair.BTC_USD, "maxSpreadOut"));
        assertEquals(BigDecimal.valueOf(0.0050), spreadService.getSpreadRecord(longExchange, shortExchange, CurrencyPair.BTC_USD, "minSpreadOut"));

        spreadService.publish(spreadRecordHighIn);

        assertEquals(BigDecimal.valueOf(-0.0050), spreadService.getSpreadRecord(longExchange, shortExchange, CurrencyPair.BTC_USD, "maxSpreadIn"));
        assertEquals(BigDecimal.valueOf(-0.0051), spreadService.getSpreadRecord(longExchange, shortExchange, CurrencyPair.BTC_USD, "minSpreadIn"));
        assertEquals(BigDecimal.valueOf(0.0050), spreadService.getSpreadRecord(longExchange, shortExchange, CurrencyPair.BTC_USD, "maxSpreadOut"));
        assertEquals(BigDecimal.valueOf(0.0050), spreadService.getSpreadRecord(longExchange, shortExchange, CurrencyPair.BTC_USD, "minSpreadOut"));
    }

    @Test
    public void testRecordHighSpreadIn() {
        Spread spreadBaseline = new Spread(
            CurrencyPair.BTC_USD,
            longExchange,
            shortExchange,
            null,
            null,
            BigDecimal.valueOf(-0.0050),
            BigDecimal.valueOf(0.0050));
        Spread spreadRecordHighIn = new Spread(
            CurrencyPair.BTC_USD,
            longExchange,
            shortExchange,
            null,
            null,
            BigDecimal.valueOf(-0.0049),
            BigDecimal.valueOf(0.0050));

        spreadService.publish(spreadBaseline);

        assertEquals(BigDecimal.valueOf(-0.0050), spreadService.getSpreadRecord(longExchange, shortExchange, CurrencyPair.BTC_USD, "maxSpreadIn"));
        assertEquals(BigDecimal.valueOf(-0.0050), spreadService.getSpreadRecord(longExchange, shortExchange, CurrencyPair.BTC_USD, "minSpreadIn"));
        assertEquals(BigDecimal.valueOf(0.0050), spreadService.getSpreadRecord(longExchange, shortExchange, CurrencyPair.BTC_USD, "maxSpreadOut"));
        assertEquals(BigDecimal.valueOf(0.0050), spreadService.getSpreadRecord(longExchange, shortExchange, CurrencyPair.BTC_USD, "minSpreadOut"));

        spreadService.publish(spreadRecordHighIn);

        assertEquals(BigDecimal.valueOf(-0.0049), spreadService.getSpreadRecord(longExchange, shortExchange, CurrencyPair.BTC_USD, "maxSpreadIn"));
        assertEquals(BigDecimal.valueOf(-0.0050), spreadService.getSpreadRecord(longExchange, shortExchange, CurrencyPair.BTC_USD, "minSpreadIn"));
        assertEquals(BigDecimal.valueOf(0.0050), spreadService.getSpreadRecord(longExchange, shortExchange, CurrencyPair.BTC_USD, "maxSpreadOut"));
        assertEquals(BigDecimal.valueOf(0.0050), spreadService.getSpreadRecord(longExchange, shortExchange, CurrencyPair.BTC_USD, "minSpreadOut"));
    }

    @Test
    public void testRecordLowSpreadOut() {
        Spread spreadBaseline = new Spread(
            CurrencyPair.BTC_USD,
            longExchange,
            shortExchange,
            null,
            null,
            BigDecimal.valueOf(-0.0050),
            BigDecimal.valueOf(0.0050));
        Spread spreadRecordHighIn = new Spread(
            CurrencyPair.BTC_USD,
            longExchange,
            shortExchange,
            null,
            null,
            BigDecimal.valueOf(-0.0050),
            BigDecimal.valueOf(0.0049));

        spreadService.publish(spreadBaseline);

        assertEquals(BigDecimal.valueOf(-0.0050), spreadService.getSpreadRecord(longExchange, shortExchange, CurrencyPair.BTC_USD, "maxSpreadIn"));
        assertEquals(BigDecimal.valueOf(-0.0050), spreadService.getSpreadRecord(longExchange, shortExchange, CurrencyPair.BTC_USD, "minSpreadIn"));
        assertEquals(BigDecimal.valueOf(0.0050), spreadService.getSpreadRecord(longExchange, shortExchange, CurrencyPair.BTC_USD, "maxSpreadOut"));
        assertEquals(BigDecimal.valueOf(0.0050), spreadService.getSpreadRecord(longExchange, shortExchange, CurrencyPair.BTC_USD, "minSpreadOut"));

        spreadService.publish(spreadRecordHighIn);

        assertEquals(BigDecimal.valueOf(-0.0050), spreadService.getSpreadRecord(longExchange, shortExchange, CurrencyPair.BTC_USD, "maxSpreadIn"));
        assertEquals(BigDecimal.valueOf(-0.0050), spreadService.getSpreadRecord(longExchange, shortExchange, CurrencyPair.BTC_USD, "minSpreadIn"));
        assertEquals(BigDecimal.valueOf(0.0050), spreadService.getSpreadRecord(longExchange, shortExchange, CurrencyPair.BTC_USD, "maxSpreadOut"));
        assertEquals(BigDecimal.valueOf(0.0049), spreadService.getSpreadRecord(longExchange, shortExchange, CurrencyPair.BTC_USD, "minSpreadOut"));
    }

    @Test
    public void testRecordHighSpreadOut() {
        Spread spreadBaseline = new Spread(
            CurrencyPair.BTC_USD,
            longExchange,
            shortExchange,
            null,
            null,
            BigDecimal.valueOf(-0.0050),
            BigDecimal.valueOf(0.0050));
        Spread spreadRecordHighIn = new Spread(
            CurrencyPair.BTC_USD,
            longExchange,
            shortExchange,
            null,
            null,
            BigDecimal.valueOf(-0.0050),
            BigDecimal.valueOf(0.0051));

        spreadService.publish(spreadBaseline);

        assertEquals(BigDecimal.valueOf(-0.0050), spreadService.getSpreadRecord(longExchange, shortExchange, CurrencyPair.BTC_USD, "maxSpreadIn"));
        assertEquals(BigDecimal.valueOf(-0.0050), spreadService.getSpreadRecord(longExchange, shortExchange, CurrencyPair.BTC_USD, "minSpreadIn"));
        assertEquals(BigDecimal.valueOf(0.0050), spreadService.getSpreadRecord(longExchange, shortExchange, CurrencyPair.BTC_USD, "maxSpreadOut"));
        assertEquals(BigDecimal.valueOf(0.0050), spreadService.getSpreadRecord(longExchange, shortExchange, CurrencyPair.BTC_USD, "minSpreadOut"));

        spreadService.publish(spreadRecordHighIn);

        assertEquals(BigDecimal.valueOf(-0.0050), spreadService.getSpreadRecord(longExchange, shortExchange, CurrencyPair.BTC_USD, "maxSpreadIn"));
        assertEquals(BigDecimal.valueOf(-0.0050), spreadService.getSpreadRecord(longExchange, shortExchange, CurrencyPair.BTC_USD, "minSpreadIn"));
        assertEquals(BigDecimal.valueOf(0.0051), spreadService.getSpreadRecord(longExchange, shortExchange, CurrencyPair.BTC_USD, "maxSpreadOut"));
        assertEquals(BigDecimal.valueOf(0.0050), spreadService.getSpreadRecord(longExchange, shortExchange, CurrencyPair.BTC_USD, "minSpreadOut"));
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
