package com.r307.arbitrader.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.r307.arbitrader.BaseTestCase;
import com.r307.arbitrader.ExchangeBuilder;
import com.r307.arbitrader.config.JsonConfiguration;
import com.r307.arbitrader.config.NotificationConfiguration;
import com.r307.arbitrader.exception.OrderNotFoundException;
import com.r307.arbitrader.config.TradingConfiguration;
import com.r307.arbitrader.service.model.ArbitrageLog;
import com.r307.arbitrader.service.telegram.TelegramClient;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.mockito.Mock;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

import static com.r307.arbitrader.DecimalConstants.BTC_SCALE;
import static com.r307.arbitrader.DecimalConstants.USD_SCALE;
import static com.r307.arbitrader.ExchangeBuilder.EXCHANGE_METADATA_PRICE_SCALE;
import static com.r307.arbitrader.ExchangeBuilder.EXCHANGE_METADATA_VOLUME_SCALE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.*;

public class TradingServiceTest extends BaseTestCase {
    private static final CurrencyPair currencyPair = new CurrencyPair("BTC/USD");
    private static final int CSV_NUMBER_OF_COLUMNS = 12;

    private Exchange longExchange;
    private Exchange shortExchange;

    private TradingConfiguration tradingConfiguration;

    @Mock
    private ExchangeService exchangeService;

    @Mock
    private SpreadService spreadService;

    private TradingService tradingService;

    @Before
    public void setUp() throws IOException {
        final JavaMailSender javaMailSenderMock = mock(JavaMailSender.class);

        ObjectMapper objectMapper = new JsonConfiguration().objectMapper();

        ConditionService conditionService = new ConditionService();
        NotificationConfiguration notificationConfiguration = new NotificationConfiguration();
        TelegramClient telegramClient = new TelegramClient(notificationConfiguration);
        ErrorCollectorService errorCollectorService = new ErrorCollectorService();

        TickerService tickerService = new TickerService(
            new TradingConfiguration(),
            exchangeService,
            errorCollectorService);
        spreadService = new SpreadService(tradingConfiguration, tickerService);
        NotificationServiceImpl notificationService = new NotificationServiceImpl(javaMailSenderMock, notificationConfiguration, telegramClient);
        tradingConfiguration = new TradingConfiguration();

        longExchange = new ExchangeBuilder("Long", CurrencyPair.BTC_USD)
                .withExchangeMetaData()
                .withTradeService()
                .withOrderBook(100, 100)
                .withBalance(Currency.USD, new BigDecimal("100.00").setScale(USD_SCALE, RoundingMode.HALF_EVEN))
                .build();
        shortExchange = new ExchangeBuilder("Short", CurrencyPair.BTC_USD)
                .withExchangeMetaData()
                .withBalance(Currency.USD, new BigDecimal("500.00").setScale(USD_SCALE, RoundingMode.HALF_EVEN))
                .build();

        // This spy right here is a bad code smell, kids! Don't try this at work!
        // Upcoming refactoring will allow me to remove it.
        tradingService = spy(new TradingService(
            objectMapper,
            tradingConfiguration,
            conditionService,
            exchangeService,
            spreadService,
            notificationService));
    }

    @Test
    public void testGetVolumeForOrder() {
        BigDecimal volume = tradingService.getVolumeForOrder(
                longExchange,
                currencyPair,
                "orderId",
                new BigDecimal("50.0"));

        assertEquals(BigDecimal.TEN, volume);
    }

    @Test
    public void testGetVolumeForOrderNull() throws IOException {
        final BigDecimal defaultValue = new BigDecimal("50.0");

        doReturn(defaultValue)
            .when(exchangeService)
            .getAccountBalance(any(Exchange.class), any(Currency.class), any(Integer.class));
        when(longExchange.getTradeService().getOrder(eq("nullOrder"))).thenReturn(null);
        when(exchangeService.convertExchangePair(any(Exchange.class), any(CurrencyPair.class)))
            .thenReturn(currencyPair);

        BigDecimal volume = tradingService.getVolumeForOrder(
            longExchange,
            currencyPair,
            "nullOrder",
            defaultValue);

        assertEquals(defaultValue, volume);
    }

    @Test(expected = OrderNotFoundException.class)
    public void testGetVolumeForOrderNotFound() {
        tradingService.getVolumeForOrder(
                longExchange,
                currencyPair,
                "missingOrder",
                new BigDecimal("50.0"));
    }

    @Test
    public void testGetVolumeForOrderNotAvailable() throws IOException {
        doReturn(new BigDecimal("90.0"))
            .when(exchangeService)
            .getAccountBalance(any(Exchange.class), any(Currency.class), any(Integer.class));

        when(exchangeService.convertExchangePair(any(Exchange.class), any(CurrencyPair.class)))
            .thenReturn(currencyPair);

        BigDecimal volume = tradingService.getVolumeForOrder(
                longExchange,
                currencyPair,
                "notAvailable",
                new BigDecimal("50.0"));

        assertEquals(new BigDecimal("90.0"), volume);
    }

    @Test
    public void testGetVolumeForOrderIOException() throws IOException {
        doReturn(new BigDecimal("90.0"))
            .when(exchangeService)
            .getAccountBalance(any(Exchange.class), any(Currency.class), any(Integer.class));

        when(exchangeService.convertExchangePair(any(Exchange.class), any(CurrencyPair.class)))
            .thenReturn(currencyPair);

        BigDecimal volume = tradingService.getVolumeForOrder(
                longExchange,
                currencyPair,
                "ioe",
                new BigDecimal("50.0"));

        assertEquals(new BigDecimal("90.0"), volume);
    }

    @Test
    public void testGetVolumeFallbackToDefaultZeroBalance() throws IOException {
        doReturn(BigDecimal.ZERO)
            .when(exchangeService)
            .getAccountBalance(any(Exchange.class), any(Currency.class), any(Integer.class));

        BigDecimal volume = tradingService.getVolumeForOrder(
            longExchange,
            currencyPair,
            "notAvailable",
            new BigDecimal("50.0"));

        assertEquals(new BigDecimal("50.0"), volume);
    }

    @Test
    public void testGetVolumeFallbackToDefaultIOException() throws IOException {
        doThrow(new IOException("Boom!"))
            .when(exchangeService)
            .getAccountBalance(any(Exchange.class), any(Currency.class), any(Integer.class));
        when(exchangeService.convertExchangePair(any(Exchange.class), any(CurrencyPair.class)))
            .thenReturn(currencyPair);

        BigDecimal volume = tradingService.getVolumeForOrder(
            longExchange,
            currencyPair,
            "notAvailable",
            new BigDecimal("50.0"));

        assertEquals(new BigDecimal("50.0"), volume);
    }

    // the best price point has enough volume to fill my order
    @Test
    public void testLimitPriceLongSufficientVolume() {
        when(exchangeService.convertExchangePair(any(Exchange.class), any(CurrencyPair.class)))
            .thenReturn(currencyPair);

        BigDecimal allowedVolume = new BigDecimal("1.00");
        BigDecimal limitPrice = tradingService.getLimitPrice(longExchange, currencyPair, allowedVolume, Order.OrderType.ASK);

        assertEquals(new BigDecimal("100.000").setScale(EXCHANGE_METADATA_PRICE_SCALE, RoundingMode.HALF_EVEN), limitPrice);
    }

    // the best price point has enough volume to fill my order
    @Test
    public void testLimitPriceShortSufficientVolume() {
        when(exchangeService.convertExchangePair(any(Exchange.class), any(CurrencyPair.class)))
            .thenReturn(currencyPair);

        BigDecimal allowedVolume = new BigDecimal("1.00");
        BigDecimal limitPrice = tradingService.getLimitPrice(longExchange, currencyPair, allowedVolume, Order.OrderType.BID);

        assertEquals(new BigDecimal("100.099").setScale(EXCHANGE_METADATA_PRICE_SCALE, RoundingMode.HALF_EVEN), limitPrice);
    }

    // the best price point isn't big enough to fill my order alone, so the price will slip
    @Test
    public void testLimitPriceLongInsufficientVolume() {
        when(exchangeService.convertExchangePair(any(Exchange.class), any(CurrencyPair.class)))
            .thenReturn(currencyPair);
        BigDecimal allowedVolume = new BigDecimal("11.00");
        BigDecimal limitPrice = tradingService.getLimitPrice(longExchange, currencyPair, allowedVolume, Order.OrderType.ASK);

        assertEquals(new BigDecimal("100.001").setScale(EXCHANGE_METADATA_PRICE_SCALE, RoundingMode.HALF_EVEN), limitPrice);
    }

    // the best price point isn't big enough to fill my order alone, so the price will slip
    @Test
    public void testLimitPriceShortInsufficientVolume() {
        when(exchangeService.convertExchangePair(any(Exchange.class), any(CurrencyPair.class)))
            .thenReturn(currencyPair);
        BigDecimal allowedVolume = new BigDecimal("11.00");
        BigDecimal limitPrice = tradingService.getLimitPrice(longExchange, currencyPair, allowedVolume, Order.OrderType.BID);

        assertEquals(new BigDecimal("100.098").setScale(EXCHANGE_METADATA_PRICE_SCALE, RoundingMode.HALF_EVEN), limitPrice);
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
    public void testComputeVolumeScale() {
        Integer result = tradingService.computeVolumeScale(longExchange, CurrencyPair.BTC_USD, BTC_SCALE);

        assertEquals(Integer.valueOf(EXCHANGE_METADATA_VOLUME_SCALE), result);
    }

    @Test
    public void testComputeVolumeScaleDefault() {
        Integer result = tradingService.computeVolumeScale(longExchange, CurrencyPair.LTC_USD, BTC_SCALE);

        assertEquals(Integer.valueOf(BTC_SCALE), result);
    }

    @Test
    public void testComputePriceScale() {
        Integer result = tradingService.computePriceScale(longExchange, CurrencyPair.BTC_USD, USD_SCALE);

        assertEquals(Integer.valueOf(EXCHANGE_METADATA_PRICE_SCALE), result);
    }

    @Test
    public void testComputePriceScaleDefault() {
        Integer result = tradingService.computePriceScale(longExchange, CurrencyPair.LTC_USD, USD_SCALE);

        assertEquals(Integer.valueOf(USD_SCALE), result);
    }

    @Test
    public void testGetMaximumExposureFixedExposure() {
        tradingConfiguration.setFixedExposure(new BigDecimal("100.00"));

        BigDecimal exposure = tradingService.getMaximumExposure(longExchange, shortExchange);

        assertEquals(new BigDecimal("100.00").setScale(USD_SCALE, RoundingMode.HALF_EVEN), exposure);
    }

    // should return 90% of the smallest account balance
    @Test
    public void testGetMaximumExposure() throws IOException {
        final BigDecimal minAccountBalance = new BigDecimal("100.00");
        final BigDecimal tradePortion = new BigDecimal("0.9");
        final BigDecimal expectedExposure = minAccountBalance.multiply(tradePortion)
            .setScale(USD_SCALE, RoundingMode.HALF_EVEN);

        when(exchangeService.getExchangeHomeCurrency(any(Exchange.class)))
            .thenReturn(Currency.USD);
        when(exchangeService.getExchangeCurrencyScale(any(Exchange.class), any(Currency.class)))
            .thenReturn(2);
        when(exchangeService.getAccountBalance(any(Exchange.class), any(Currency.class), anyInt()))
            .thenReturn(minAccountBalance);

        BigDecimal actual = tradingService.getMaximumExposure(longExchange, shortExchange);

        assertEquals(expectedExposure, actual);
    }

    @Test
    public void testGetMaximumExposureEmpty() {
        BigDecimal exposure = tradingService.getMaximumExposure();

        assertEquals(new BigDecimal("0.00").setScale(USD_SCALE, RoundingMode.HALF_EVEN), exposure);
    }

    @Test
    public void testGetMaximumExposureException() throws IOException {
        when(exchangeService.getAccountBalance(shortExchange, Currency.USD, USD_SCALE)).thenThrow(new IOException("Boom!"));

        BigDecimal exposure = tradingService.getMaximumExposure();

        // the IOE should not propagate and blow everything up
        assertEquals(new BigDecimal("0.00").setScale(USD_SCALE, RoundingMode.HALF_EVEN), exposure);
    }

    @Test
    public void testLogArbitrageToCsv() throws IOException {
        final File file = new File(TradingScheduler.TRADE_HISTORY_FILE);
        FileUtils.deleteQuietly(file);

        final ArbitrageLog arbitrageLog = ArbitrageLog.ArbitrageLogBuilder.builder()
            .withShortExchange("CoinbasePro")
            .withShortCurrency("BTC/USD")
            .withShortSpread(new BigDecimal("0.008"))
            .withShortSlip(new BigDecimal("-0.001"))
            .withShortAmount(BigDecimal.valueOf(10))
            .withLongExchange("Bitstamp")
            .withLongCurrency("BTC/USD")
            .withLongSpread(new BigDecimal("-0.003"))
            .withLongSlip(new BigDecimal("-0.001"))
            .withLongAmount(BigDecimal.valueOf(10))
            .withProfit(new BigDecimal("0.01"))
            .withTimestamp(OffsetDateTime.now())
            .build();

        tradingService.persistArbitrageToCsvFile(arbitrageLog);

        List<String> lines = FileUtils.readLines(file, StandardCharsets.UTF_8);
        assertEquals(2, lines.size());

        // Get headers
        String[] split = lines.get(0).split("\",\"");
        assertEquals("Header size does not match number of columns", CSV_NUMBER_OF_COLUMNS, split.length);

        // Assert headers does not end with a comma
        final String lastColumn = split[split.length - 1];
        assertFalse(lastColumn.endsWith(","));

        // Append one line
        tradingService.persistArbitrageToCsvFile(arbitrageLog);

        lines = FileUtils.readLines(file, StandardCharsets.UTF_8);
        assertEquals(3, lines.size());

        split = lines.get(2).split("\",\"");
        assertEquals("Number of elements (columns) per line does not match the number of columns", CSV_NUMBER_OF_COLUMNS, split.length);

        FileUtils.deleteQuietly(file);
    }
}
