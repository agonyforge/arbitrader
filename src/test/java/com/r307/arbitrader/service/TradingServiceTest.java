package com.r307.arbitrader.service;

import com.r307.arbitrader.ExchangeBuilder;
import com.r307.arbitrader.config.NotificationConfiguration;
import com.r307.arbitrader.exception.OrderNotFoundException;
import com.r307.arbitrader.config.TradingConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

import static com.r307.arbitrader.DecimalConstants.BTC_SCALE;
import static com.r307.arbitrader.DecimalConstants.USD_SCALE;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class TradingServiceTest {
    private static final CurrencyPair currencyPair = new CurrencyPair("BTC/USD");

    private Exchange longExchange;
    private Exchange shortExchange;

    private TradingConfiguration tradingConfiguration;

    private TradingService tradingService;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);

        tradingConfiguration = new TradingConfiguration();
        NotificationConfiguration notificationConfiguration = new NotificationConfiguration();
        ExchangeFeeCache feeCache = new ExchangeFeeCache();
        ConditionService conditionService = new ConditionService();

        longExchange = new ExchangeBuilder("Long", CurrencyPair.BTC_USD)
                .withExchangeMetaData()
                .withTradeService()
                .withOrderBook(100, 100)
                .withBalance(Currency.USD, new BigDecimal(100.00).setScale(USD_SCALE, RoundingMode.HALF_EVEN))
                .build();
        shortExchange = new ExchangeBuilder("Short", CurrencyPair.BTC_USD)
                .withExchangeMetaData()
                .withBalance(Currency.USD, new BigDecimal(500.00).setScale(USD_SCALE, RoundingMode.HALF_EVEN))
                .build();

        // This spy right here is a bad code smell, kids! Don't try this at work!
        // Upcoming refactoring will allow me to remove it.
        tradingService = spy(new TradingService(
            tradingConfiguration,
            notificationConfiguration,
            feeCache,
            conditionService));
    }

    @Test
    public void testGetVolumeForOrder() {
        BigDecimal volume = tradingService.getVolumeForOrder(
                longExchange,
                "orderId",
                new BigDecimal(50.0));

        assertEquals(BigDecimal.TEN, volume);
    }

    @Test(expected = OrderNotFoundException.class)
    public void testGetVolumeForOrderNotFound() {
        tradingService.getVolumeForOrder(
                longExchange,
                "missingOrder",
                new BigDecimal(50.0));
    }

    @Test
    public void testGetVolumeForOrderNotAvailable() {
        BigDecimal volume = tradingService.getVolumeForOrder(
                longExchange,
                "notAvailable",
                new BigDecimal(50.0));

        assertEquals(new BigDecimal(50.0), volume);
    }

    @Test
    public void testGetVolumeForOrderIOException() {
        BigDecimal volume = tradingService.getVolumeForOrder(
                longExchange,
                "ioe",
                new BigDecimal(50.0));

        assertEquals(new BigDecimal(50.0), volume);
    }

    // the best price point has enough volume to fill my order
    @Test
    public void testLimitPriceLongSufficientVolume() {
        BigDecimal allowedVolume = new BigDecimal(1.00);
        BigDecimal limitPrice = tradingService.getLimitPrice(longExchange, currencyPair, allowedVolume, Order.OrderType.ASK);

        assertEquals(new BigDecimal(100.0000).setScale(BTC_SCALE, RoundingMode.HALF_EVEN), limitPrice);
    }

    // the best price point has enough volume to fill my order
    @Test
    public void testLimitPriceShortSufficientVolume() {
        BigDecimal allowedVolume = new BigDecimal(1.00);
        BigDecimal limitPrice = tradingService.getLimitPrice(longExchange, currencyPair, allowedVolume, Order.OrderType.BID);

        assertEquals(new BigDecimal(100.0990).setScale(BTC_SCALE, RoundingMode.HALF_EVEN), limitPrice);
    }

    // the best price point isn't big enough to fill my order alone, so the price will slip
    @Test
    public void testLimitPriceLongInsufficientVolume() {
        BigDecimal allowedVolume = new BigDecimal(11.00);
        BigDecimal limitPrice = tradingService.getLimitPrice(longExchange, currencyPair, allowedVolume, Order.OrderType.ASK);

        assertEquals(new BigDecimal(100.0010).setScale(BTC_SCALE, RoundingMode.HALF_EVEN), limitPrice);
    }

    // the best price point isn't big enough to fill my order alone, so the price will slip
    @Test
    public void testLimitPriceShortInsufficientVolume() {
        BigDecimal allowedVolume = new BigDecimal(11.00);
        BigDecimal limitPrice = tradingService.getLimitPrice(longExchange, currencyPair, allowedVolume, Order.OrderType.BID);

        assertEquals(new BigDecimal(100.0980).setScale(BTC_SCALE, RoundingMode.HALF_EVEN), limitPrice);
    }

    // the exchange doesn't have enough volume to fill my gigantic order
    @Test(expected = RuntimeException.class)
    public void testLimitPriceLongInsufficientLiquidity() {
        BigDecimal allowedVolume = new BigDecimal(10001);

        tradingService.getLimitPrice(longExchange, currencyPair, allowedVolume, Order.OrderType.ASK);
    }

    // the exchange doesn't have enough volume to fill my gigantic order
    @Test(expected = RuntimeException.class)
    public void testLimitPriceShortInsufficientLiquidity() {
        BigDecimal allowedVolume = new BigDecimal(10001);

        tradingService.getLimitPrice(longExchange, currencyPair, allowedVolume, Order.OrderType.BID);
    }

    @Test
    public void testGetMaximumExposureFixedExposure() {
        tradingConfiguration.setFixedExposure(new BigDecimal(100.00));

        BigDecimal exposure = tradingService.getMaximumExposure(longExchange, shortExchange);

        assertEquals(new BigDecimal(100.00).setScale(USD_SCALE, RoundingMode.HALF_EVEN), exposure);
    }

    // should return 90% of the smallest account balance
    @Test
    public void testGetMaximumExposure() {
        BigDecimal exposure = tradingService.getMaximumExposure(longExchange, shortExchange);

        assertEquals(new BigDecimal(90.00).setScale(USD_SCALE, RoundingMode.HALF_EVEN), exposure);
    }

    @Test
    public void testGetMaximumExposureEmpty() {
        BigDecimal exposure = tradingService.getMaximumExposure();

        assertEquals(new BigDecimal(0.00).setScale(USD_SCALE, RoundingMode.HALF_EVEN), exposure);
    }

    @Test
    public void testGetMaximumExposureException() throws IOException {
        when(tradingService.getAccountBalance(shortExchange, Currency.USD, USD_SCALE)).thenThrow(new IOException("Boom!"));

        BigDecimal exposure = tradingService.getMaximumExposure();

        // the IOE should not propagate and blow everything up
        assertEquals(new BigDecimal(0.00).setScale(USD_SCALE, RoundingMode.HALF_EVEN), exposure);
    }
}
