package com.r307.arbitrader.service;

import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ExchangeFeeCacheTest {
    @Mock
    private Exchange exchange;

    @Mock
    private ExchangeSpecification exchangeSpecification;

    private CurrencyPair currencyPair;

    private ExchangeFeeCache exchangeFeeCache;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(exchange.getExchangeSpecification()).thenReturn(exchangeSpecification);
        when(exchangeSpecification.getExchangeName()).thenReturn("WhatCoin");

        currencyPair = new CurrencyPair("COIN", "USD");

        exchangeFeeCache = new ExchangeFeeCache();
    }

    @Test
    public void testSetAndGet() {
        exchangeFeeCache.setCachedFee(exchange, currencyPair, new BigDecimal(0.0025));

        assertEquals(new BigDecimal(0.0025), exchangeFeeCache.getCachedFee(exchange, currencyPair));
    }

    @Test
    public void testGetUnknownPair() {
        CurrencyPair altPair = new CurrencyPair("FAKE", "USD");

        assertNull(exchangeFeeCache.getCachedFee(exchange, altPair));
    }

    @Test
    public void testGetUnknownExchange() {
        Exchange altExchange = mock(Exchange.class);
        ExchangeSpecification altSpec = mock(ExchangeSpecification.class);

        when(altExchange.getExchangeSpecification()).thenReturn(altSpec);
        when(altSpec.getExchangeName()).thenReturn("AltEx");

        assertNull(exchangeFeeCache.getCachedFee(altExchange, currencyPair));
    }
}
