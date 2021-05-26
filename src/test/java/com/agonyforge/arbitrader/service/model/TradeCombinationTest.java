package com.agonyforge.arbitrader.service.model;

import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertEquals;

public class TradeCombinationTest {
    @Mock
    private Exchange longExchange;

    @Mock
    private Exchange shortExchange;

    private CurrencyPair currencyPair = CurrencyPair.BTC_USD;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testCreateAndGet() {
        TradeCombination tradeCombination = new TradeCombination(longExchange, shortExchange, currencyPair);

        assertEquals(longExchange, tradeCombination.getLongExchange());
        assertEquals(shortExchange, tradeCombination.getShortExchange());
        assertEquals(currencyPair, tradeCombination.getCurrencyPair());
    }
}
