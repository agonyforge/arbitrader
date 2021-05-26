package com.agonyforge.arbitrader.service;

import com.agonyforge.arbitrader.ExchangeBuilder;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.List;

import static com.agonyforge.arbitrader.service.ErrorCollectorService.HEADER;
import static org.junit.Assert.*;

public class ErrorCollectorServiceTest {
    private static final String EXCHANGE_NAME = "ExceptionalCoins";

    private Exchange exchange;

    private ErrorCollectorService errorCollectorService;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);

        exchange = new ExchangeBuilder(EXCHANGE_NAME, CurrencyPair.BTC_USD)
            .build();

        errorCollectorService = new ErrorCollectorService();
    }

    @Test
    public void testCollect() {
        errorCollectorService.collect(exchange, new NullPointerException("Boom!"));

        List<String> report = errorCollectorService.report();

        assertEquals(2, report.size());
        assertEquals(HEADER, report.get(0));
        assertEquals(EXCHANGE_NAME + ": NullPointerException Boom! x 1", report.get(1));
    }

    @Test
    public void testCollectIncrementsByOne() {
        errorCollectorService.collect(exchange, new NullPointerException("Boom!"));
        errorCollectorService.collect(exchange, new NullPointerException("Boom!"));

        List<String> report = errorCollectorService.report();

        assertEquals(2, report.size());
        assertEquals(HEADER, report.get(0));
        assertEquals(EXCHANGE_NAME + ": NullPointerException Boom! x 2", report.get(1));
    }

    @Test
    public void testEmptyReport() {
        List<String> report = errorCollectorService.report();

        assertEquals(1, report.size());
        assertEquals(HEADER, report.get(0));
    }

    @Test
    public void testIsEmpty() {
        assertTrue(errorCollectorService.isEmpty());

        errorCollectorService.collect(exchange, new NullPointerException());

        assertFalse(errorCollectorService.isEmpty());
    }

    @Test
    public void testClear() {
        errorCollectorService.collect(exchange, new NullPointerException());

        assertFalse(errorCollectorService.isEmpty());

        errorCollectorService.clear();

        assertTrue(errorCollectorService.isEmpty());
    }
}
