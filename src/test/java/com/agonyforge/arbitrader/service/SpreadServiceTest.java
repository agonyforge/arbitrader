package com.agonyforge.arbitrader.service;

import com.agonyforge.arbitrader.ExchangeBuilder;
import com.agonyforge.arbitrader.BaseTestCase;
import com.agonyforge.arbitrader.config.TradingConfiguration;
import com.agonyforge.arbitrader.service.model.ExchangeFee;
import com.agonyforge.arbitrader.service.model.Spread;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.mockito.Mock;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

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

    @Test
    public void testComputeSpread() {
        BigDecimal longPrice = new BigDecimal("10.00000000");
        BigDecimal shortPrice = new BigDecimal("15.00000000");

        BigDecimal spread = spreadService.computeSpread(longPrice, shortPrice);

        assertEquals(0, new BigDecimal("0.50000000").compareTo(spread));
    }

    @Test
    public void testGetEntrySpreadTarget() {
        TradingConfiguration tradingConfiguration = new TradingConfiguration();
        tradingConfiguration.setEntrySpreadTarget(new BigDecimal("0.001"));
        ExchangeFee longFee = new ExchangeFee(new BigDecimal("0.005"), null);
        ExchangeFee shortFee = new ExchangeFee(new BigDecimal("0.0026"), BigDecimal.ZERO);

        BigDecimal entrySpreadTarget = spreadService.getEntrySpreadTarget(tradingConfiguration, longFee, shortFee);
        assertEquals(new BigDecimal("0.008627431321436").setScale(8, RoundingMode.HALF_EVEN), entrySpreadTarget.setScale(8, RoundingMode.HALF_EVEN));
    }

    @Test
    public void testGetExitSpreadTarget_fromProfit() {
        TradingConfiguration tradingConfiguration = new TradingConfiguration();
        tradingConfiguration.setMinimumProfit(new BigDecimal("0.001"));
        ExchangeFee longFee = new ExchangeFee(new BigDecimal("0.005"), null);
        ExchangeFee shortFee = new ExchangeFee(new BigDecimal("0.0026"), BigDecimal.ZERO);
        BigDecimal entrySpread = new BigDecimal("0.008627431321436");

        BigDecimal exitSpreadTarget = spreadService.getExitSpreadTarget(tradingConfiguration, entrySpread, longFee, shortFee);
        assertEquals(new BigDecimal("-0.007580291242769").setScale(8, RoundingMode.HALF_EVEN), exitSpreadTarget.setScale(8, RoundingMode.HALF_EVEN));

    }

    @Test
    public void testGetExitSpreadTarget_fromSpreadTarget() {
        TradingConfiguration tradingConfiguration = new TradingConfiguration();
        tradingConfiguration.setExitSpreadTarget(new BigDecimal("0.001"));
        ExchangeFee longFee = new ExchangeFee(new BigDecimal("0.005"), null);
        ExchangeFee shortFee = new ExchangeFee(new BigDecimal("0.0026"), BigDecimal.ZERO);
        BigDecimal entrySpread = new BigDecimal("0.008627431321436");

        BigDecimal exitSpreadTarget = spreadService.getExitSpreadTarget(tradingConfiguration, entrySpread, longFee, shortFee);

        assertEquals(new BigDecimal("-0.006587871534012").setScale(8, RoundingMode.HALF_EVEN), exitSpreadTarget.setScale(8, RoundingMode.HALF_EVEN));

    }
}
