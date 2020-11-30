package com.r307.arbitrader.service;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static com.r307.arbitrader.service.ConditionService.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class ConditionServiceTest {
    private static final String TEST_EXCHANGE_NAME = "Test Exchange";

    @Mock
    private Exchange exchange;

    @Mock
    private ExchangeSpecification exchangeSpecification;

    private ConditionService conditionService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(exchange.getExchangeSpecification()).thenReturn(exchangeSpecification);
        when(exchangeSpecification.getExchangeName()).thenReturn(TEST_EXCHANGE_NAME);

        conditionService = new ConditionService();
    }

    @AfterClass
    public static void tearDown() {
        FileUtils.deleteQuietly(new File(BLACKOUT));
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
