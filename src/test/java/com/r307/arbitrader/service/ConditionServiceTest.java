package com.r307.arbitrader.service;

import com.r307.arbitrader.BaseTestCase;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.mockito.Mock;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static com.r307.arbitrader.service.ConditionService.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

public class ConditionServiceTest extends BaseTestCase {
    private static final String TEST_EXCHANGE_NAME = "Test Exchange";

    @Mock
    private Exchange exchange;

    @Mock
    private ExchangeSpecification exchangeSpecification;

    private ConditionService conditionService;

    @AfterClass
    public static void afterClass() {
        FileUtils.deleteQuietly(new File(FORCE_OPEN));
        FileUtils.deleteQuietly(new File(FORCE_CLOSE));
        FileUtils.deleteQuietly(new File(EXIT_WHEN_IDLE));
        FileUtils.deleteQuietly(new File(STATUS));
        FileUtils.deleteQuietly(new File(BLACKOUT));
    }

    @Before
    public void setUp() {
        when(exchange.getExchangeSpecification()).thenReturn(exchangeSpecification);
        when(exchangeSpecification.getExchangeName()).thenReturn(TEST_EXCHANGE_NAME);

        conditionService = new ConditionService();
    }

    @Test
    public void testClearForceOpenConditionIdempotence() {
        // it should not break if the condition is already clear
        conditionService.clearForceOpenCondition();
    }

    @Test
    public void testClearForceOpenCondition() throws IOException {
        File forceOpen = new File(FORCE_OPEN);

        assertTrue(forceOpen.createNewFile());
        assertTrue(forceOpen.exists());

        conditionService.clearForceOpenCondition();

        assertFalse(forceOpen.exists());
    }

    @Test
    public void testCheckForceOpenCondition() throws IOException {
        File forceOpen = new File(FORCE_OPEN);
        CurrencyPair currencyPair = CurrencyPair.BTC_USD;
        String longExchangeName = "CrazyCoinz";
        String shortExchangeName = "CoinBazaar";

        assertFalse(forceOpen.exists());
        assertFalse(conditionService.isForceOpenCondition(currencyPair, longExchangeName, shortExchangeName));

        FileUtils.writeStringToFile(forceOpen,"BTC/USD CrazyCoinz/CoinBazaar", Charset.defaultCharset());

        assertTrue(forceOpen.exists());
        assertTrue(conditionService.isForceOpenCondition(currencyPair, longExchangeName, shortExchangeName));

        FileUtils.deleteQuietly(forceOpen);
    }

    @Test
    public void testCheckForceOpenConditionWrongExchange() throws IOException {
        File forceOpen = new File(FORCE_OPEN);
        CurrencyPair currencyPair = CurrencyPair.BTC_USD;
        String longExchangeName = "CoinGuru";
        String shortExchangeName = "CoinBazaar";

        assertFalse(forceOpen.exists());
        assertFalse(conditionService.isForceOpenCondition(currencyPair, longExchangeName, shortExchangeName));

        FileUtils.writeStringToFile(forceOpen,"BTC/USD CrazyCoins/CoinBazaar", Charset.defaultCharset());

        assertTrue(forceOpen.exists());
        assertFalse(conditionService.isForceOpenCondition(currencyPair, longExchangeName, shortExchangeName));

        FileUtils.deleteQuietly(forceOpen);
    }

    @Test
    public void testCheckForceOpenConditionWrongPair() throws IOException {
        File forceOpen = new File(FORCE_OPEN);
        CurrencyPair currencyPair = CurrencyPair.XRP_USD;
        String longExchangeName = "CrazyCoinz";
        String shortExchangeName = "CoinBazaar";

        assertFalse(forceOpen.exists());
        assertFalse(conditionService.isForceOpenCondition(currencyPair, longExchangeName, shortExchangeName));

        FileUtils.writeStringToFile(forceOpen,"BTC/USD CrazyCoins/CoinBazaar", Charset.defaultCharset());

        assertTrue(forceOpen.exists());
        assertFalse(conditionService.isForceOpenCondition(currencyPair, longExchangeName, shortExchangeName));

        FileUtils.deleteQuietly(forceOpen);
    }

    @Test
    public void testClearForceCloseConditionIdempotence() {
        // it should not break if the condition is already clear
        conditionService.clearForceCloseCondition();
    }

    @Test
    public void testClearForceCloseCondition() throws IOException {
        File forceClose = new File(FORCE_CLOSE);

        assertTrue(forceClose.createNewFile());
        assertTrue(forceClose.exists());

        conditionService.clearForceCloseCondition();

        assertFalse(forceClose.exists());
    }

    @Test
    public void testCheckForceCloseCondition() throws IOException {
        File forceClose = new File(FORCE_CLOSE);

        assertFalse(forceClose.exists());
        assertFalse(conditionService.isForceCloseCondition());

        assertTrue(forceClose.createNewFile());

        assertTrue(forceClose.exists());
        assertTrue(conditionService.isForceCloseCondition());

        FileUtils.deleteQuietly(forceClose);
    }

    @Test
    public void testClearExitWhenIdleConditionIdempotence() {
        conditionService.clearExitWhenIdleCondition();
    }

    @Test
    public void testClearExitWhenIdleCondition() throws IOException {
        File exitWhenIdle = new File(EXIT_WHEN_IDLE);

        assertTrue(exitWhenIdle.createNewFile());
        assertTrue(exitWhenIdle.exists());

        conditionService.clearExitWhenIdleCondition();

        assertFalse(exitWhenIdle.exists());
    }

    @Test
    public void testCheckExitWhenIdleCondition() throws IOException {
        File exitWhenIdle = new File(EXIT_WHEN_IDLE);

        assertFalse(exitWhenIdle.exists());
        assertFalse(conditionService.isExitWhenIdleCondition());

        assertTrue(exitWhenIdle.createNewFile());

        assertTrue(exitWhenIdle.exists());
        assertTrue(conditionService.isExitWhenIdleCondition());

        FileUtils.deleteQuietly(exitWhenIdle);
    }

    @Test
    public void testClearStatusCondition() throws IOException {
        File status = new File(STATUS);

        assertTrue(status.createNewFile());
        assertTrue(status.exists());

        conditionService.clearStatusCondition();

        assertFalse(status.exists());
    }

    @Test
    public void testStatusCondition() throws IOException {
        File status = new File(STATUS);

        assertFalse(status.exists());
        assertFalse(conditionService.isStatusCondition());

        assertTrue(status.createNewFile());

        assertTrue(status.exists());
        assertTrue(conditionService.isStatusCondition());

        FileUtils.deleteQuietly(status);
    }

    @Test
    public void testNoFileBlackoutCondition() {
        File blackoutFile = new File(BLACKOUT);

        assertFalse(blackoutFile.exists());
        assertFalse(conditionService.isBlackoutCondition(exchange));
    }

    @Test
    public void testFutureBlackoutBlackoutCondition() throws IOException {
        File blackoutFile = new File(BLACKOUT);
        PrintWriter writer = new PrintWriter(new FileOutputStream(blackoutFile));

        // blackout occurs in the future
        ZonedDateTime blackoutStart = ZonedDateTime.now().plusHours(1L);
        ZonedDateTime blackoutEnd = ZonedDateTime.now().plusHours(2L);

        writer.println(String.format("%s,%s,%s",
            TEST_EXCHANGE_NAME,
            blackoutStart.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            blackoutEnd.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)));
        writer.close();

        assertFalse(conditionService.isBlackoutCondition(exchange));

        FileUtils.deleteQuietly(blackoutFile);
    }

    @Test
    public void testPastBlackoutBlackoutCondition() throws IOException {
        File blackoutFile = new File(BLACKOUT);
        PrintWriter writer = new PrintWriter(new FileOutputStream(blackoutFile));

        // blackout occurs in the past
        ZonedDateTime blackoutStart = ZonedDateTime.now().minusHours(2L);
        ZonedDateTime blackoutEnd = ZonedDateTime.now().minusHours(1L);

        writer.println(String.format("%s,%s,%s",
            TEST_EXCHANGE_NAME,
            blackoutStart.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            blackoutEnd.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)));
        writer.close();

        assertFalse(conditionService.isBlackoutCondition(exchange));

        FileUtils.deleteQuietly(blackoutFile);
    }

    @Test
    public void testCurrentBlackoutBlackoutCondition() throws IOException {
        File blackoutFile = new File(BLACKOUT);
        PrintWriter writer = new PrintWriter(new FileOutputStream(blackoutFile));

        // blackout is in progress
        ZonedDateTime blackoutStart = ZonedDateTime.now().minusHours(1L);
        ZonedDateTime blackoutEnd = ZonedDateTime.now().plusHours(1L);

        writer.println(String.format("%s,%s,%s",
            TEST_EXCHANGE_NAME,
            blackoutStart.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            blackoutEnd.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)));
        writer.close();

        assertTrue(conditionService.isBlackoutCondition(exchange));

        FileUtils.deleteQuietly(blackoutFile);
    }

    @Test
    public void testCurrentBlackoutForOtherExchangeBlackoutCondition() throws IOException {
        File blackoutFile = new File(BLACKOUT);
        PrintWriter writer = new PrintWriter(new FileOutputStream(blackoutFile));

        // blackout is in progress
        ZonedDateTime blackoutStart = ZonedDateTime.now().minusHours(1L);
        ZonedDateTime blackoutEnd = ZonedDateTime.now().plusHours(1L);

        writer.println(String.format("%s,%s,%s",
            "Other Exchange",
            blackoutStart.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            blackoutEnd.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)));
        writer.close();

        assertFalse(conditionService.isBlackoutCondition(exchange));

        FileUtils.deleteQuietly(blackoutFile);
    }
}
